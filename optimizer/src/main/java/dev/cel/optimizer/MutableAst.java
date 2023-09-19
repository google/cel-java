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

import com.google.common.collect.ImmutableList;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExprIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory;

/** MutableAst contains logic for mutating a {@link CelExpr}. */
@Internal
final class MutableAst {
  private static final int MAX_ITERATION_COUNT = 500;
  private final CelExpr.Builder newExpr;
  private final long exprIdToReplace;
  private final CelExprIdGenerator celExprIdGenerator;
  private int iterationCount;

  private MutableAst(CelExprIdGenerator celExprIdGenerator, CelExpr.Builder newExpr, long exprId) {
    this.celExprIdGenerator = celExprIdGenerator;
    this.newExpr = newExpr;
    this.exprIdToReplace = exprId;
  }

  /**
   * Replaces a subtree in the given CelExpr. This is a very dangerous operation. Callers should
   * re-typecheck the mutated AST and additionally verify that the resulting AST is semantically
   * valid.
   *
   * <p>This method should remain package-private.
   */
  static CelExpr replaceSubtree(CelExpr root, CelExpr newExpr, long exprIdToReplace) {
    // Zero out the expr IDs in the new expression tree first. This ensures that no ID collision
    // occurs while attempting to replace the subtree, potentially leading to infinite loop
    CelExpr.Builder newExprBuilder = newExpr.toBuilder();
    MutableAst mutableAst = new MutableAst(() -> 0, CelExpr.newBuilder(), -1);
    newExprBuilder = mutableAst.visit(newExprBuilder);

    // Replace the subtree
    mutableAst =
        new MutableAst(
            CelExprIdGeneratorFactory.newMonotonicIdGenerator(0), newExprBuilder, exprIdToReplace);

    // TODO: Normalize IDs for macro calls

    return mutableAst.visit(root.toBuilder()).build();
  }

  private CelExpr.Builder visit(CelExpr.Builder expr) {
    if (++iterationCount > MAX_ITERATION_COUNT) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (expr.id() == exprIdToReplace) {
      return visit(newExpr);
    }

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
        // TODO: Implement functionality.
        throw new UnsupportedOperationException("Augmenting comprehensions is not supported yet.");
      case CONSTANT: // Fall-through is intended.
      case IDENT:
        return expr.setId(celExprIdGenerator.nextExprId());
      default:
        throw new IllegalArgumentException("unexpected expr kind: " + expr.exprKind().getKind());
    }
  }

  private CelExpr.Builder visit(CelExpr.Builder celExpr, CelSelect.Builder selectExpr) {
    CelExpr.Builder visitedOperand = visit(selectExpr.operand().toBuilder());
    selectExpr = selectExpr.setOperand(visitedOperand.build());

    return celExpr.setSelect(selectExpr.build()).setId(celExprIdGenerator.nextExprId());
  }

  private CelExpr.Builder visit(CelExpr.Builder celExpr, CelCall.Builder callExpr) {
    if (callExpr.target().isPresent()) {
      CelExpr.Builder visitedTargetExpr = visit(callExpr.target().get().toBuilder());
      callExpr = callExpr.setTarget(visitedTargetExpr.build());

      celExpr.setCall(callExpr.build());
    }

    ImmutableList<CelExpr> args = callExpr.args();
    for (int i = 0; i < args.size(); i++) {
      CelExpr arg = args.get(i);
      CelExpr.Builder visitedArg = visit(arg.toBuilder());
      callExpr.setArg(i, visitedArg.build());
    }

    return celExpr.setCall(callExpr.build()).setId(celExprIdGenerator.nextExprId());
  }

  private CelExpr.Builder visit(CelExpr.Builder celExpr, CelCreateList.Builder createListBuilder) {
    ImmutableList<CelExpr> elements = createListBuilder.getElements();
    for (int i = 0; i < elements.size(); i++) {
      CelExpr.Builder visitedElement = visit(elements.get(i).toBuilder());
      createListBuilder.setElement(i, visitedElement.build());
    }

    return celExpr.setCreateList(createListBuilder.build()).setId(celExprIdGenerator.nextExprId());
  }

  private CelExpr.Builder visit(
      CelExpr.Builder celExpr, CelCreateStruct.Builder createStructBuilder) {
    ImmutableList<CelCreateStruct.Entry> entries = createStructBuilder.getEntries();
    for (int i = 0; i < entries.size(); i++) {
      CelCreateStruct.Entry.Builder entryBuilder =
          entries.get(i).toBuilder().setId(celExprIdGenerator.nextExprId());
      CelExpr.Builder visitedValue = visit(entryBuilder.value().toBuilder());
      entryBuilder.setValue(visitedValue.build());

      createStructBuilder.setEntry(i, entryBuilder.build());
    }

    return celExpr
        .setCreateStruct(createStructBuilder.build())
        .setId(celExprIdGenerator.nextExprId());
  }

  private CelExpr.Builder visit(CelExpr.Builder celExpr, CelCreateMap.Builder createMapBuilder) {
    ImmutableList<CelCreateMap.Entry> entries = createMapBuilder.getEntries();
    for (int i = 0; i < entries.size(); i++) {
      CelCreateMap.Entry.Builder entryBuilder =
          entries.get(i).toBuilder().setId(celExprIdGenerator.nextExprId());
      CelExpr.Builder visitedKey = visit(entryBuilder.key().toBuilder());
      entryBuilder.setKey(visitedKey.build());
      CelExpr.Builder visitedValue = visit(entryBuilder.value().toBuilder());
      entryBuilder.setValue(visitedValue.build());

      createMapBuilder.setEntry(i, entryBuilder.build());
    }

    return celExpr.setCreateMap(createMapBuilder.build()).setId(celExprIdGenerator.nextExprId());
  }
}
