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
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.parser.Operator;
import java.util.HashMap;
import java.util.Optional;

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
public class CSEOptimizer implements CelAstOptimizer {

  private static final CSEOptimizer INSTANCE = new CSEOptimizer(CSEOptions.newBuilder().build());
  private static final String BIND_IDENTIFIER_PREFIX = "@r";
  private static final String MANGLED_COMPREHENSION_IDENTIFIER_PREFIX = "@c";
  private static final ImmutableSet<String> CSE_ALLOWED_FUNCTIONS =
      Streams.concat(
              stream(Operator.values()).map(Operator::getFunction),
              stream(Standard.Function.values()).map(Standard.Function::getFunction))
          .collect(toImmutableSet());
  private final CSEOptions cseOptions;

  /** Returns a default instance of CSE optimizer with preconfigured defaults. */
  public static CSEOptimizer getInstance() {
    return INSTANCE;
  }

  /** Returns a new instance of CSE optimizer configured with the provided {@link CSEOptions}. */
  public static CSEOptimizer newInstance(CSEOptions cseOptions) {
    return new CSEOptimizer(cseOptions);
  }

  @Override
  public CelAbstractSyntaxTree optimize(CelNavigableAst navigableAst, Cel cel)
      throws CelOptimizationException {
    CelAbstractSyntaxTree newAst =
        mangleComprehensionIdentifierNames(
            navigableAst.getAst(), MANGLED_COMPREHENSION_IDENTIFIER_PREFIX);
    CelSource newSource = newAst.getSource();
    int bindIdentifierIndex = 0;
    int iterCount;
    for (iterCount = 0; iterCount < cseOptions.maxIterationLimit(); iterCount++) {
      CelNavigableExpr cseCandidate = findCseCandidate(newAst).orElse(null);
      if (cseCandidate == null) {
        break;
      }

      String bindIdentifier = BIND_IDENTIFIER_PREFIX + bindIdentifierIndex;
      bindIdentifierIndex++;

      // Replace all CSE candidates with bind identifier
      for (; iterCount < cseOptions.maxIterationLimit(); iterCount++) {
        CelExpr exprToReplace =
            CelNavigableAst.fromAst(newAst)
                .getRoot()
                .allNodes()
                .filter(CSEOptimizer::canEliminate)
                .filter(node -> areSemanticallyEqual(cseCandidate.expr(), node.expr()))
                .map(CelNavigableExpr::expr)
                .findAny().orElse(null);
        if (exprToReplace == null) {
          break;
        }

        CelExpr newBindExpr = CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName(bindIdentifier).build()).build();
        if (exprToReplace.selectOrDefault().testOnly()) {
          // Rewrap the bind identifier into presence test
          newBindExpr = CelExpr.ofSelectExpr(exprToReplace.id(), newBindExpr, exprToReplace.select().field(), true);
        }

        newAst =
            replaceSubtree(
                newAst,
                newBindExpr,
                exprToReplace.id());
      }

      // Find LCA to insert the new cel.bind macro into.
      CelNavigableExpr lca = getLca(newAst, bindIdentifier);

      newSource =
          newSource.toBuilder().addAllMacroCalls(newAst.getSource().getMacroCalls()).build();
      newAst = CelAbstractSyntaxTree.newParsedAst(newAst.getExpr(), newSource);

      // Insert the new bind call
      newAst =
          replaceSubtreeWithNewBindMacro(
              newAst, bindIdentifier, cseCandidate.expr(), lca.expr(), lca.id());

      // Retain the existing macro calls in case if the bind identifiers are replacing a subtree
      // that contains a comprehension.
      newSource = newAst.getSource();
    }

    if (iterCount >= cseOptions.maxIterationLimit()) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (iterCount == 0) {
      // No modification has been made.
      return navigableAst.getAst();
    }

    if (!cseOptions.populateMacroCalls()) {
      newAst = CelAbstractSyntaxTree.newParsedAst(newAst.getExpr(), CelSource.newBuilder().build());
    }

    return renumberIdsConsecutively(newAst);
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
    HashMap<CelExpr, CelNavigableExpr> encounteredNodes = new HashMap<>();
    ImmutableList<CelNavigableExpr> allNodes =
        CelNavigableAst.fromAst(ast)
            .getRoot()
            .allNodes(TraversalOrder.PRE_ORDER)
            .filter(CSEOptimizer::canEliminate)
            .collect(toImmutableList());

    for (CelNavigableExpr node : allNodes) {
      // Strip out all IDs to test equivalence
      CelExpr celExpr = normalizeForEquality(node.expr());
      if (encounteredNodes.containsKey(celExpr)) {
        return Optional.of(encounteredNodes.get(celExpr));
      }

      encounteredNodes.put(celExpr, node);
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
                // result, loopStep or iterRange.
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
   * Converts the {@link CelExpr} to make it suitable for performing semantically equals check in {@link #areSemanticallyEqual(CelExpr, CelExpr)}.
   *
   * Specifically, this will:
   *
   * <ul>
   *   <li>Set all expr IDs in the expression tree to 0.</li>
   *   <li>Strip all presence tests (i.e: testOnly is marked as false on {@link CelExpr.ExprKind.Kind#SELECT}</li>
   * </ul>
   **/
  private CelExpr normalizeForEquality(CelExpr celExpr) {
    int iterCount;
    for (iterCount = 0; iterCount < cseOptions.maxIterationLimit(); iterCount++) {
      Optional<CelExpr> presenceTestExpr = CelNavigableExpr.fromExpr(celExpr)
          .allNodes()
          .map(CelNavigableExpr::expr)
          .filter(expr -> expr.selectOrDefault().testOnly())
          .findAny();
      if (!presenceTestExpr.isPresent()) {
        break;
      }

      CelExpr newExpr = presenceTestExpr.get().toBuilder()
          .setSelect(presenceTestExpr.get().select().toBuilder().setTestOnly(false).build())
          .build();

      celExpr = replaceSubtree(celExpr, newExpr, newExpr.id());
    }

    if (iterCount >= cseOptions.maxIterationLimit()) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    return clearExprIds(celExpr);
  }

  /** Options to configure how Common Subexpression Elimination behave. */
  @AutoValue
  public abstract static class CSEOptions {
    public abstract int maxIterationLimit();

    public abstract boolean populateMacroCalls();

    /** Builder for configuring the {@link CSEOptions}. */
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

      public abstract CSEOptions build();

      Builder() {}
    }

    /** Returns a new options builder with recommended defaults pre-configured. */
    public static Builder newBuilder() {
      return new AutoValue_CSEOptimizer_CSEOptions.Builder()
          .maxIterationLimit(500)
          .populateMacroCalls(false);
    }

    CSEOptions() {}
  }

  private CSEOptimizer(CSEOptions cseOptions) {
    this.cseOptions = cseOptions;
  }
}
