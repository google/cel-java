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
import dev.cel.checker.Standard;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.parser.Operator;
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
  private static final ImmutableSet<String> CSE_ALLOWED_FUNCTIONS =
      Streams.concat(
              stream(Operator.values()).map(Operator::getFunction),
              stream(Standard.Function.values()).map(Standard.Function::getFunction))
          .collect(toImmutableSet());
  private final SubexpressionOptimizerOptions cseOptions;

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
  public CelAbstractSyntaxTree optimize(CelNavigableAst navigableAst, Cel cel) {
    CelAbstractSyntaxTree astToModify =
        mangleComprehensionIdentifierNames(
            navigableAst.getAst(), MANGLED_COMPREHENSION_IDENTIFIER_PREFIX);
    CelSource sourceToModify = astToModify.getSource();

    int bindIdentifierIndex = 0;
    int iterCount;
    for (iterCount = 0; iterCount < cseOptions.maxIterationLimit(); iterCount++) {
      CelNavigableExpr cseCandidate = findCseCandidate(astToModify).orElse(null);
      if (cseCandidate == null) {
        break;
      }

      String bindIdentifier = BIND_IDENTIFIER_PREFIX + bindIdentifierIndex;
      bindIdentifierIndex++;

      // Using the CSE candidate, fetch all semantically equivalent subexpressions ahead of time.
      ImmutableList<CelExpr> allCseCandidates =
          getAllCseCandidatesStream(astToModify, cseCandidate.expr()).collect(toImmutableList());

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
            replaceSubtree(
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
          replaceSubtreeWithNewBindMacro(
              astToModify, bindIdentifier, cseCandidate.expr(), lca.expr(), lca.id());

      // Retain the existing macro calls in case if the bind identifiers are replacing a subtree
      // that contains a comprehension.
      sourceToModify = astToModify.getSource();
    }

    if (iterCount >= cseOptions.maxIterationLimit()) {
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

    return renumberIdsConsecutively(astToModify);
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
      // Strip out all IDs to test equivalence
      CelExpr celExpr = clearExprIds(node.expr());
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
    return clearExprIds(expr1).equals(clearExprIds(expr2));
  }

  private static boolean isAllowedFunction(CelNavigableExpr navigableExpr) {
    if (navigableExpr.getKind().equals(Kind.CALL)) {
      return CSE_ALLOWED_FUNCTIONS.contains(navigableExpr.expr().call().function());
    }

    return true;
  }

  /** Options to configure how Common Subexpression Elimination behave. */
  @AutoValue
  public abstract static class SubexpressionOptimizerOptions {
    public abstract int maxIterationLimit();

    public abstract boolean populateMacroCalls();

    /** Builder for configuring the {@link SubexpressionOptimizerOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /**
       * Limit the number of iteration while performing CSE. An exception is thrown if the iteration
       * count exceeds the set value.
       */
      public abstract Builder maxIterationLimit(int value);

      /**
       * Populate the macro_calls map in source_info with macro calls on the resulting optimized
       * AST.
       */
      public abstract Builder populateMacroCalls(boolean value);

      public abstract SubexpressionOptimizerOptions build();

      Builder() {}
    }

    /** Returns a new options builder with recommended defaults pre-configured. */
    public static Builder newBuilder() {
      return new AutoValue_SubexpressionOptimizer_SubexpressionOptimizerOptions.Builder()
          .maxIterationLimit(500)
          .populateMacroCalls(false);
    }

    SubexpressionOptimizerOptions() {}
  }

  private SubexpressionOptimizer(SubexpressionOptimizerOptions cseOptions) {
    this.cseOptions = cseOptions;
  }
}
