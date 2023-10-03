// Copyright 2023 Google LLC
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

package dev.cel.optimizer;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExprIdGeneratorFactory;
import dev.cel.common.ast.CelExprIdGeneratorFactory.MonotonicIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory.StableIdGenerator;
import dev.cel.common.navigation.CelNavigableExpr;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

/** MutableAst contains logic for mutating a {@link CelExpr}. */
@Internal
final class MutableAst {
  private static final int MAX_ITERATION_COUNT = 500;
  private final CelExpr.Builder newExpr;
  private final ExprIdGenerator celExprIdGenerator;
  private int iterationCount;
  private long exprIdToReplace;

  private MutableAst(ExprIdGenerator celExprIdGenerator, CelExpr.Builder newExpr, long exprId) {
    this.celExprIdGenerator = celExprIdGenerator;
    this.newExpr = newExpr;
    this.exprIdToReplace = exprId;
  }

  /**
   * Replaces a subtree in the given CelExpr.
   *
   * <p>This method should remain package-private.
   */
  static CelAbstractSyntaxTree replaceSubtree(
      CelAbstractSyntaxTree ast, CelExpr newExpr, long exprIdToReplace) {
    // Update the IDs in the new expression tree first. This ensures that no ID collision
    // occurs while attempting to replace the subtree, potentially leading to infinite loop
    MonotonicIdGenerator monotonicIdGenerator =
        CelExprIdGeneratorFactory.newMonotonicIdGenerator(getMaxId(ast.getExpr()));
    CelExpr.Builder newExprBuilder =
        renumberExprIds((unused) -> monotonicIdGenerator.nextExprId(), newExpr.toBuilder());

    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(0);
    CelExpr.Builder mutatedRoot =
        replaceSubtreeImpl(
            stableIdGenerator::renumberId,
            ast.getExpr().toBuilder(),
            newExprBuilder,
            exprIdToReplace);

    // If the source info contained macro call information, their IDs must be normalized.
    CelSource normalizedSource =
        normalizeMacroSource(
            ast.getSource(), exprIdToReplace, mutatedRoot, stableIdGenerator::renumberId);

    return CelAbstractSyntaxTree.newParsedAst(mutatedRoot.build(), normalizedSource);
  }

  private static CelSource normalizeMacroSource(
      CelSource celSource,
      long exprIdToReplace,
      CelExpr.Builder mutatedRoot,
      ExprIdGenerator idGenerator) {
    // Remove the macro metadata that no longer exists in the AST due to being replaced.
    celSource = celSource.toBuilder().clearMacroCall(exprIdToReplace).build();
    if (celSource.getMacroCalls().isEmpty()) {
      return CelSource.newBuilder().build();
    }

    CelSource.Builder sourceBuilder = CelSource.newBuilder();
    ImmutableMap<Long, CelExpr> allExprs =
        CelNavigableExpr.fromExpr(mutatedRoot.build())
            .allNodes()
            .map(CelNavigableExpr::expr)
            .collect(
                toImmutableMap(
                    CelExpr::id,
                    expr -> expr,
                    (expr1, expr2) -> {
                      // Comprehensions can reuse same expression (result). We just need to ensure
                      // that they are identical.
                      if (expr1.equals(expr2)) {
                        return expr1;
                      }
                      throw new IllegalStateException(
                          "Expected expressions to be the same for id: " + expr1.id());
                    }));

    // Update the macro call IDs and their call references
    for (Entry<Long, CelExpr> macroCall : celSource.getMacroCalls().entrySet()) {
      long macroId = macroCall.getKey();
      long callId = idGenerator.generate(macroId);

      CelExpr.Builder newCall = renumberExprIds(idGenerator, macroCall.getValue().toBuilder());
      CelNavigableExpr callNav = CelNavigableExpr.fromExpr(newCall.build());
      ImmutableList<CelExpr> callDescendants =
          callNav.descendants().map(CelNavigableExpr::expr).collect(toImmutableList());

      for (CelExpr callChild : callDescendants) {
        if (!allExprs.containsKey(callChild.id())) {
          continue;
        }
        CelExpr mutatedExpr = allExprs.get(callChild.id());
        if (!callChild.equals(mutatedExpr)) {
          newCall =
              replaceSubtreeImpl((arg) -> arg, newCall, mutatedExpr.toBuilder(), callChild.id());
        }
      }
      sourceBuilder.addMacroCalls(callId, newCall.build());
    }

    return sourceBuilder.build();
  }

  private static CelExpr.Builder replaceSubtreeImpl(
      ExprIdGenerator idGenerator,
      CelExpr.Builder root,
      CelExpr.Builder newExpr,
      long exprIdToReplace) {
    MutableAst mutableAst = new MutableAst(idGenerator, newExpr, exprIdToReplace);
    return mutableAst.visit(root);
  }

  private static CelExpr.Builder renumberExprIds(
      ExprIdGenerator idGenerator, CelExpr.Builder root) {
    MutableAst mutableAst = new MutableAst(idGenerator, root, Integer.MIN_VALUE);
    return mutableAst.visit(root);
  }

  private static long getMaxId(CelExpr newExpr) {
    return CelNavigableExpr.fromExpr(newExpr)
        .allNodes()
        .mapToLong(node -> node.expr().id())
        .max()
        .orElseThrow(NoSuchElementException::new);
  }

  private CelExpr.Builder visit(CelExpr.Builder expr) {
    if (++iterationCount > MAX_ITERATION_COUNT) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (expr.id() == exprIdToReplace) {
      exprIdToReplace = Integer.MIN_VALUE; // Marks that the subtree has been replaced.
      return visit(newExpr.setId(expr.id()));
    }

    expr.setId(celExprIdGenerator.generate(expr.id()));

    switch (expr.exprKind().getKind()) {
      case SELECT:
        return visit(expr, expr.select().toBuilder());
      case CALL:
        return visit(expr, expr.call().toBuilder());
      case CREATE_LIST:
        return visit(expr, expr.createList().toBuilder());
      case CREATE_STRUCT:
        return visit(expr, expr.createStruct().toBuilder());
      case CREATE_MAP:
        return visit(expr, expr.createMap().toBuilder());
      case COMPREHENSION:
        return visit(expr, expr.comprehension().toBuilder());
      case CONSTANT: // Fall-through is intended
      case IDENT:
      case NOT_SET: // Note: comprehension arguments can contain a not set expr.
        return expr;
      default:
        throw new IllegalArgumentException("unexpected expr kind: " + expr.exprKind().getKind());
    }
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelSelect.Builder select) {
    select.setOperand(visit(select.operand().toBuilder()).build());
    return expr.setSelect(select.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCall.Builder call) {
    if (call.target().isPresent()) {
      call.setTarget(visit(call.target().get().toBuilder()).build());
    }
    ImmutableList<CelExpr.Builder> argsBuilders = call.getArgsBuilders();
    for (int i = 0; i < argsBuilders.size(); i++) {
      CelExpr.Builder arg = argsBuilders.get(i);
      call.setArg(i, visit(arg).build());
    }

    return expr.setCall(call.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCreateStruct.Builder createStruct) {
    ImmutableList<CelCreateStruct.Entry.Builder> entries = createStruct.getEntriesBuilders();
    for (int i = 0; i < entries.size(); i++) {
      CelCreateStruct.Entry.Builder entry = entries.get(i);
      entry.setValue(visit(entry.value().toBuilder()).build());

      createStruct.setEntry(i, entry.build());
    }

    return expr.setCreateStruct(createStruct.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCreateMap.Builder createMap) {
    ImmutableList<CelCreateMap.Entry.Builder> entriesBuilders = createMap.getEntriesBuilders();
    for (int i = 0; i < entriesBuilders.size(); i++) {
      CelCreateMap.Entry.Builder entry = entriesBuilders.get(i);
      entry.setKey(visit(entry.key().toBuilder()).build());
      entry.setValue(visit(entry.value().toBuilder()).build());

      createMap.setEntry(i, entry.build());
    }

    return expr.setCreateMap(createMap.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCreateList.Builder createList) {
    ImmutableList<CelExpr.Builder> elementsBuilders = createList.getElementsBuilders();
    for (int i = 0; i < elementsBuilders.size(); i++) {
      CelExpr.Builder elem = elementsBuilders.get(i);
      createList.setElement(i, visit(elem).build());
    }

    return expr.setCreateList(createList.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelComprehension.Builder comprehension) {
    comprehension.setIterRange(visit(comprehension.iterRange().toBuilder()).build());
    comprehension.setAccuInit(visit(comprehension.accuInit().toBuilder()).build());
    comprehension.setLoopCondition(visit(comprehension.loopCondition().toBuilder()).build());
    comprehension.setLoopStep(visit(comprehension.loopStep().toBuilder()).build());
    comprehension.setResult(visit(comprehension.result().toBuilder()).build());

    return expr.setComprehension(comprehension.build());
  }

  @FunctionalInterface
  private interface ExprIdGenerator {

    /** Generates an expression ID based on the provided ID. */
    long generate(long exprId);
  }
}
