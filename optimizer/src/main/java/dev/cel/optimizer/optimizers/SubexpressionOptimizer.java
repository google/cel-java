// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.optimizer.optimizers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.checker.Standard;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.MutableAst;
import dev.cel.parser.Operator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Performs Common Subexpression Elimination.
 *
 * <pre>
 * Subexpressions are extracted into `cel.bind` calls. For example, the expression below:
 *
 * {@code
 *    message.child.text_map[x].startsWith("hello") && message.child.text_map[x].endsWith("world")
 * }
 *
 * will be optimized into the following form:
 *
 * {@code
 *    cel.bind(@r0, message.child.text_map[x],
 *        @r0.startsWith("hello") && @r0.endsWith("world"))
 * }
 * </pre>
 */
public class SubexpressionOptimizer implements CelAstOptimizer {
  private static final SubexpressionOptimizer INSTANCE =
      new SubexpressionOptimizer(SubexpressionOptimizerOptions.newBuilder().build());
  private static final String BIND_IDENTIFIER_PREFIX = "@r";
  private static final String MANGLED_COMPREHENSION_IDENTIFIER_PREFIX = "@c";

  private static final String CEL_BLOCK_FUNCTION = "cel.@block";
  private static final String BLOCK_INDEX_PREFIX = "@index";
  private static final ImmutableSet<String> CSE_ALLOWED_FUNCTIONS =
      Streams.concat(
              stream(Operator.values()).map(Operator::getFunction),
              stream(Standard.Function.values()).map(Standard.Function::getFunction))
          .collect(toImmutableSet());
  private final SubexpressionOptimizerOptions cseOptions;
  private final MutableAst mutableAst;

  /**
   * Returns a default instance of common subexpression elimination optimizer with preconfigured
   * defaults.
   */
  public static SubexpressionOptimizer getInstance() {
    return INSTANCE;
  }

  /**
   * Returns a new instance of common subexpression elimination optimizer configured with the
   * provided {@link SubexpressionOptimizerOptions}.
   */
  public static SubexpressionOptimizer newInstance(SubexpressionOptimizerOptions cseOptions) {
    return new SubexpressionOptimizer(cseOptions);
  }

  @Override
  public CelAbstractSyntaxTree optimize(CelNavigableAst navigableAst, CelBuilder celBuilder) {
    return cseOptions.enableCelBlock() ? optimizeUsingCelBlock(navigableAst, celBuilder) : optimizeUsingCelBind(navigableAst);
  }

  private CelAbstractSyntaxTree optimizeUsingCelBlock(CelNavigableAst navigableAst, CelBuilder celBuilder) {
    CelType resultType = navigableAst.getAst().getResultType();
    celBuilder.addFunctionDeclarations(CelFunctionDecl.newFunctionDeclaration(CEL_BLOCK_FUNCTION,
        CelOverloadDecl.newGlobalOverload("cel_block_list", resultType, ListType.create(SimpleType.DYN), resultType)
    ));
    CelAbstractSyntaxTree astToModify =
        mutableAst.mangleComprehensionIdentifierNames(
            navigableAst.getAst(), MANGLED_COMPREHENSION_IDENTIFIER_PREFIX);
    // CelAbstractSyntaxTree astToModify = navigableAst.getAst();
    CelSource sourceToModify = astToModify.getSource();

    int blockIdentifierIndex = 0;
    int iterCount;
    ArrayList<CelExpr> subexpressions = new ArrayList<>();
    for (iterCount = 0; iterCount < cseOptions.iterationLimit(); iterCount++) {
      CelExpr cseCandidate = findCseCandidate(astToModify).map(CelNavigableExpr::expr).orElse(null);
      if (cseCandidate == null) {
        break;
      }

      subexpressions.add(cseCandidate);

      String blockIdentifier = BLOCK_INDEX_PREFIX + blockIdentifierIndex;
      blockIdentifierIndex++;

      // Using the CSE candidate, fetch all semantically equivalent subexpressions ahead of time.
      ImmutableList<CelExpr> allCseCandidates =
          getAllCseCandidatesStream(astToModify, cseCandidate).collect(toImmutableList());

      // Replace all CSE candidates with new bind identifier
      for (CelExpr semanticallyEqualNode : allCseCandidates) {
        iterCount++;
        // Refetch the candidate expr as mutating the AST could have renumbered its IDs.
        CelExpr exprToReplace =
            getAllCseCandidatesStream(astToModify, semanticallyEqualNode)
                .findAny()
                .orElseThrow(
                    () ->
                        new NoSuchElementException(
                            "No value present for expr ID: " + semanticallyEqualNode.id()));

        astToModify =
            mutableAst.replaceSubtree(
                astToModify,
                CelExpr.newBuilder()
                    .setIdent(CelIdent.newBuilder().setName(blockIdentifier).build())
                    .build(),
                exprToReplace.id());
      }

      sourceToModify =
          sourceToModify.toBuilder()
              .addAllMacroCalls(astToModify.getSource().getMacroCalls())
              .build();
      astToModify = CelAbstractSyntaxTree.newParsedAst(astToModify.getExpr(), sourceToModify);
      // Retain the existing macro calls in case if the block identifiers are replacing a subtree
      // that contains a comprehension.
      sourceToModify = astToModify.getSource();
    }

    if (iterCount >= cseOptions.iterationLimit()) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (iterCount == 0) {
      // No modification has been made.
      return astToModify;
    }

    // Type-check subexpressions
    celBuilder.setResultType(SimpleType.DYN);
    for (int i = 0; i < subexpressions.size(); i++) {
      CelExpr subexpression = subexpressions.get(i);

      CelAbstractSyntaxTree subAst = CelAbstractSyntaxTree.newParsedAst(subexpression,
          CelSource.newBuilder().build());

      try {
        subAst = celBuilder.build().check(subAst).getAst();
      } catch (CelValidationException e) {
        throw new IllegalStateException(e);
      }

      celBuilder.addVar("@index" + i, subAst.getResultType());
    }


    // for (CelExpr subExpr : subexpressions) {
    //   CelAbstractSyntaxTree subAst = CelAbstractSyntaxTree.newParsedAst(subExpr, CelSource.newBuilder().build());
    //
    //   try {
    //     subAst = cel.check(subAst).getAst();
    //   } catch (CelValidationException e) {
    //     throw new IllegalStateException(e);
    //   }
    //   System.out.println(subAst);
    // }

    // Insert cel.@block
    CelExpr blockExpr = CelExpr.newBuilder().setCall(
        CelCall.newBuilder().setFunction(CEL_BLOCK_FUNCTION)
            .addArgs
                (
                    CelExpr.newBuilder()
                        .setId(1)
                        .setCreateList(CelCreateList.newBuilder()
                            .addElements(subexpressions)
                            .build()
                    ).build(),
                    astToModify.getExpr()
                  )
            .build())
        .build();
    CelAbstractSyntaxTree celBlockAst = CelAbstractSyntaxTree.newParsedAst(blockExpr, astToModify.getSource());
    celBlockAst = mutableAst.renumberIdsConsecutively(celBlockAst);

    // astToModify = mutableAst.replaceSubtree(astToModify, blockExpr, astToModify.getExpr().id());
    // astToModify = mutableAst.replaceSubtree(celBlockAst,

    if (!cseOptions.populateMacroCalls()) {
      astToModify =
          CelAbstractSyntaxTree.newParsedAst(astToModify.getExpr(), CelSource.newBuilder().build());
    }

    celBuilder.setResultType(resultType);
    try {
      // CelAbstractSyntaxTree checkedAst = celBuilder.build().check(mutableAst.renumberIdsConsecutively(astToModify)).getAst();
      CelAbstractSyntaxTree checkedAst = celBuilder.build().check(celBlockAst).getAst();
      return checkedAst;
    } catch (CelValidationException e) {
      throw new RuntimeException(e);
    }
  }

  private CelAbstractSyntaxTree optimizeUsingCelBind(CelNavigableAst navigableAst) {
    CelAbstractSyntaxTree astToModify =
        mutableAst.mangleComprehensionIdentifierNames(
            navigableAst.getAst(), MANGLED_COMPREHENSION_IDENTIFIER_PREFIX);
    CelSource sourceToModify = astToModify.getSource();

    int bindIdentifierIndex = 0;
    int iterCount;
    for (iterCount = 0; iterCount < cseOptions.iterationLimit(); iterCount++) {
      CelExpr cseCandidate = findCseCandidate(astToModify).map(CelNavigableExpr::expr).orElse(null);
      if (cseCandidate == null) {
        break;
      }

      String bindIdentifier = BIND_IDENTIFIER_PREFIX + bindIdentifierIndex;
      bindIdentifierIndex++;

      // Using the CSE candidate, fetch all semantically equivalent subexpressions ahead of time.
      ImmutableList<CelExpr> allCseCandidates =
          getAllCseCandidatesStream(astToModify, cseCandidate).collect(toImmutableList());

      // Replace all CSE candidates with new bind identifier
      for (CelExpr semanticallyEqualNode : allCseCandidates) {
        iterCount++;
        // Refetch the candidate expr as mutating the AST could have renumbered its IDs.
        CelExpr exprToReplace =
            getAllCseCandidatesStream(astToModify, semanticallyEqualNode)
                .findAny()
                .orElseThrow(
                    () ->
                        new NoSuchElementException(
                            "No value present for expr ID: " + semanticallyEqualNode.id()));

        astToModify =
            mutableAst.replaceSubtree(
                astToModify,
                CelExpr.newBuilder()
                    .setIdent(CelIdent.newBuilder().setName(bindIdentifier).build())
                    .build(),
                exprToReplace.id());
      }

      // Find LCA to insert the new cel.bind macro into.
      CelNavigableExpr lca = getLca(astToModify, bindIdentifier);

      sourceToModify =
          sourceToModify.toBuilder()
              .addAllMacroCalls(astToModify.getSource().getMacroCalls())
              .build();
      astToModify = CelAbstractSyntaxTree.newParsedAst(astToModify.getExpr(), sourceToModify);

      // Insert the new bind call
      astToModify =
          mutableAst.replaceSubtreeWithNewBindMacro(
              astToModify, bindIdentifier, cseCandidate, lca.expr(), lca.id());

      // Retain the existing macro calls in case if the bind identifiers are replacing a subtree
      // that contains a comprehension.
      sourceToModify = astToModify.getSource();
    }

    if (iterCount >= cseOptions.iterationLimit()) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (iterCount == 0) {
      // No modification has been made.
      return astToModify;
    }

    if (!cseOptions.populateMacroCalls()) {
      astToModify =
          CelAbstractSyntaxTree.newParsedAst(astToModify.getExpr(), CelSource.newBuilder().build());
    }

    return mutableAst.renumberIdsConsecutively(astToModify);
  }

  private Stream<CelExpr> getAllCseCandidatesStream(
      CelAbstractSyntaxTree ast, CelExpr cseCandidate) {
    return CelNavigableAst.fromAst(ast)
        .getRoot()
        .allNodes()
        .filter(SubexpressionOptimizer::canEliminate)
        .map(CelNavigableExpr::expr)
        .filter(expr -> areSemanticallyEqual(cseCandidate, expr));
  }

  private static CelNavigableExpr getLca(CelAbstractSyntaxTree ast, String boundIdentifier) {
    CelNavigableExpr root = CelNavigableAst.fromAst(ast).getRoot();
    ImmutableList<CelNavigableExpr> allNodesWithIdentifier =
        root.allNodes()
            .filter(node -> node.expr().identOrDefault().name().equals(boundIdentifier))
            .collect(toImmutableList());

    if (allNodesWithIdentifier.size() < 2) {
      throw new IllegalStateException("Expected at least 2 bound identifiers to be present.");
    }

    CelNavigableExpr lca = root;
    long lcaAncestorCount = 0;
    HashMap<Long, Long> ancestors = new HashMap<>();
    for (CelNavigableExpr navigableExpr : allNodesWithIdentifier) {
      Optional<CelNavigableExpr> maybeParent = Optional.of(navigableExpr);
      while (maybeParent.isPresent()) {
        CelNavigableExpr parent = maybeParent.get();
        if (!ancestors.containsKey(parent.id())) {
          ancestors.put(parent.id(), 1L);
          continue;
        }

        long ancestorCount = ancestors.get(parent.id());
        if (lcaAncestorCount < ancestorCount
            || (lcaAncestorCount == ancestorCount && lca.depth() < parent.depth())) {
          lca = parent;
          lcaAncestorCount = ancestorCount;
        }

        ancestors.put(parent.id(), ancestorCount + 1);
        maybeParent = parent.parent();
      }
    }

    return lca;
  }

  private Optional<CelNavigableExpr> findCseCandidate(CelAbstractSyntaxTree ast) {
    HashSet<CelExpr> encounteredNodes = new HashSet<>();
    ImmutableList<CelNavigableExpr> allNodes =
        CelNavigableAst.fromAst(ast)
            .getRoot()
            .allNodes(TraversalOrder.PRE_ORDER)
            .filter(SubexpressionOptimizer::canEliminate)
            .collect(toImmutableList());

    for (CelNavigableExpr node : allNodes) {
      // Normalize the expr to test semantic equivalence.
      CelExpr celExpr = normalizeForEquality(node.expr());
      if (encounteredNodes.contains(celExpr)) {
        return Optional.of(node);
      }

      encounteredNodes.add(celExpr);
    }

    return Optional.empty();
  }

  private static boolean canEliminate(CelNavigableExpr navigableExpr) {
    return !navigableExpr.getKind().equals(Kind.CONSTANT)
        && !navigableExpr.getKind().equals(Kind.IDENT)
        && !navigableExpr.expr().identOrDefault().name().startsWith(BIND_IDENTIFIER_PREFIX)
        && !navigableExpr.expr().selectOrDefault().testOnly()
        && isAllowedFunction(navigableExpr)
        && isWithinInlineableComprehension(navigableExpr);
  }

  private static boolean isWithinInlineableComprehension(CelNavigableExpr expr) {
    Optional<CelNavigableExpr> maybeParent = expr.parent();
    while (maybeParent.isPresent()) {
      CelNavigableExpr parent = maybeParent.get();
      if (parent.getKind().equals(Kind.COMPREHENSION)) {
        return Streams.concat(
                // If the expression is within a comprehension, it is eligible for CSE iff is in
                // result, loopStep or iterRange. While result is not human authored, it needs to be
                // included to extract subexpressions that are already in cel.bind macro.
                CelNavigableExpr.fromExpr(parent.expr().comprehension().result()).descendants(),
                CelNavigableExpr.fromExpr(parent.expr().comprehension().loopStep()).descendants(),
                CelNavigableExpr.fromExpr(parent.expr().comprehension().iterRange()).allNodes())
            .filter(
                node ->
                    // Exclude empty lists (cel.bind sets this for iterRange).
                    !node.getKind().equals(Kind.CREATE_LIST)
                        || !node.expr().createList().elements().isEmpty())
            .map(CelNavigableExpr::expr)
            .anyMatch(node -> node.equals(expr.expr()));
      }
      maybeParent = parent.parent();
    }

    return true;
  }

  private boolean areSemanticallyEqual(CelExpr expr1, CelExpr expr2) {
    return normalizeForEquality(expr1).equals(normalizeForEquality(expr2));
  }

  private static boolean isAllowedFunction(CelNavigableExpr navigableExpr) {
    if (navigableExpr.getKind().equals(Kind.CALL)) {
      return CSE_ALLOWED_FUNCTIONS.contains(navigableExpr.expr().call().function());
    }

    return true;
  }

  /**
   * Converts the {@link CelExpr} to make it suitable for performing semantically equals check in
   * {@link #areSemanticallyEqual(CelExpr, CelExpr)}.
   *
   * <p>Specifically, this will:
   *
   * <ul>
   *   <li>Set all expr IDs in the expression tree to 0.
   *   <li>Strip all presence tests (i.e: testOnly is marked as false on {@link
   *       CelExpr.ExprKind.Kind#SELECT}
   * </ul>
   */
  private CelExpr normalizeForEquality(CelExpr celExpr) {
    int iterCount;
    for (iterCount = 0; iterCount < cseOptions.iterationLimit(); iterCount++) {
      CelExpr presenceTestExpr =
          CelNavigableExpr.fromExpr(celExpr)
              .allNodes()
              .map(CelNavigableExpr::expr)
              .filter(expr -> expr.selectOrDefault().testOnly())
              .findAny()
              .orElse(null);
      if (presenceTestExpr == null) {
        break;
      }

      CelExpr newExpr =
          presenceTestExpr.toBuilder()
              .setSelect(presenceTestExpr.select().toBuilder().setTestOnly(false).build())
              .build();

      celExpr = mutableAst.replaceSubtree(celExpr, newExpr, newExpr.id());
    }

    if (iterCount >= cseOptions.iterationLimit()) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    return mutableAst.clearExprIds(celExpr);
  }

  /** Options to configure how Common Subexpression Elimination behave. */
  @AutoValue
  public abstract static class SubexpressionOptimizerOptions {
    public abstract int iterationLimit();

    public abstract boolean populateMacroCalls();
    public abstract boolean enableCelBlock();

    /** Builder for configuring the {@link SubexpressionOptimizerOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /**
       * Limit the number of iteration while performing CSE. An exception is thrown if the iteration
       * count exceeds the set value.
       */
      public abstract Builder iterationLimit(int value);

      /**
       * Populate the macro_calls map in source_info with macro calls on the resulting optimized
       * AST.
       */
      public abstract Builder populateMacroCalls(boolean value);

      public abstract Builder enableCelBlock(boolean value);

      public abstract SubexpressionOptimizerOptions build();

      Builder() {}
    }

    /** Returns a new options builder with recommended defaults pre-configured. */
    public static Builder newBuilder() {
      return new AutoValue_SubexpressionOptimizer_SubexpressionOptimizerOptions.Builder()
          .iterationLimit(500)
          .populateMacroCalls(false)
          .enableCelBlock(false);
    }

    SubexpressionOptimizerOptions() {}
  }

  private SubexpressionOptimizer(SubexpressionOptimizerOptions cseOptions) {
    this.cseOptions = cseOptions;
    this.mutableAst = MutableAst.newInstance(cseOptions.iterationLimit());
  }
}
