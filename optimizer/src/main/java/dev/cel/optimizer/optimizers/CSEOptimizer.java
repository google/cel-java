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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import dev.cel.bundle.Cel;
import dev.cel.checker.Standard;
import dev.cel.common.CelAbstractSyntaxTree;
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
import java.util.HashSet;
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
 *    cel.bind(m, message.child.text_map[x],
 *        m.startsWith("hello") && m.endsWith("world"))
 * }
 * </pre>
 */
public class CSEOptimizer implements CelAstOptimizer {

  public static final CSEOptimizer INSTANCE = new CSEOptimizer();
  private static final int MAX_ITERATION_COUNT = 1000;
  private static final String BIND_IDENTIFIER_PREFIX = "@r";
  private static final ImmutableSet<String> CSE_ALLOWED_FUNCTIONS =
      Streams.concat(
              stream(Operator.values()).map(Operator::getFunction),
              stream(Standard.Function.values()).map(Standard.Function::getFunction))
          .collect(toImmutableSet());

  @Override
  public CelAbstractSyntaxTree optimize(CelNavigableAst navigableAst, Cel cel)
      throws CelOptimizationException {

    CelAbstractSyntaxTree newAst = navigableAst.getAst();
    int bindIdentifierIndex = 0;
    int iterCount = 0;
    while (iterCount < MAX_ITERATION_COUNT) {
      iterCount++;
      Optional<CelNavigableExpr> cseCandidate = findCseCandidate(newAst);
      if (!cseCandidate.isPresent()) {
        break;
      }

      String bindIdentifier = BIND_IDENTIFIER_PREFIX + bindIdentifierIndex;
      bindIdentifierIndex++;

      // Replace all CSE candidates with bind identifier
      while (iterCount < MAX_ITERATION_COUNT) {
        iterCount++;
        Optional<CelNavigableExpr> exprToReplace =
            CelNavigableAst.fromAst(newAst)
                .getRoot()
                .allNodes()
                .filter(CSEOptimizer::canEliminate)
                .filter(node -> areSemanticallyEqual(cseCandidate.get().expr(), node.expr()))
                .findAny();

        if (!exprToReplace.isPresent()) {
          break;
        }

        newAst =
            replaceSubtree(
                newAst,
                CelExpr.newBuilder()
                    .setIdent(CelIdent.newBuilder().setName(bindIdentifier).build())
                    .build(),
                exprToReplace.get().expr().id());
      }

      // Find LCA to insert the new cel.bind macro into.
      // CelNavigableExpr lca = getLca(newAst, bindIdentifier);
      CelNavigableExpr lca = getLca(newAst, bindIdentifier);

      newAst =
          replaceSubtreeWithBindMacro(
              newAst, bindIdentifier, cseCandidate.get().expr(), lca.expr());
    }

    return newAst;
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
        if (!ancestors.containsKey(parent.expr().id())) {
          ancestors.put(parent.expr().id(), 1L);
          continue;
        }

        long ancestorCount = ancestors.get(parent.expr().id());
        if (lcaAncestorCount < ancestorCount
            || (lcaAncestorCount == ancestorCount && lca.depth() < parent.depth())) {
          lca = parent;
          lcaAncestorCount = ancestorCount;
        }

        ancestors.put(parent.expr().id(), ancestorCount + 1);
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
            .filter(CSEOptimizer::canEliminate)
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
        && !navigableExpr.getKind().equals(Kind.COMPREHENSION)
        && !navigableExpr.parent().map(p -> p.getKind().equals(Kind.COMPREHENSION)).orElse(false)
        && isAllowedFunction(navigableExpr)
        && !navigableExpr.expr().identOrDefault().name().startsWith(BIND_IDENTIFIER_PREFIX);
    // && !navigableExpr.expr().identOrDefault().name().equals("__result__");
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

  private CSEOptimizer() {}
}
