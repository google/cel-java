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

package dev.cel.optimizer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExprIdGeneratorFactory.ExprIdGenerator;

/**
 * MutableExprVisitor performs mutation of {@link CelExpr} based on its configured parameters.
 *
 * <p>This class is NOT thread-safe. Callers should spawn a new instance of this class each time the
 * expression is being mutated.
 *
 * <p>Note that CelExpr is immutable by design. Therefore, the logic here doesn't actually mutate
 * the existing expression tree. Instead, a brand new CelExpr is produced with the subtree swapped
 * at the desired expression ID to replace.
 */
@Internal
final class MutableExprVisitor {
  private final CelExpr.Builder newExpr;
  private final ExprIdGenerator celExprIdGenerator;
  private final long iterationLimit;
  private int iterationCount;
  private long exprIdToReplace;

  static MutableExprVisitor newInstance(
      ExprIdGenerator idGenerator,
      CelExpr.Builder newExpr,
      long exprIdToReplace,
      long iterationLimit) {
    // iterationLimit * 2, because the expr can be walked twice due to the immutable nature of
    // CelExpr.
    return new MutableExprVisitor(idGenerator, newExpr, exprIdToReplace, iterationLimit * 2);
  }

  CelExpr.Builder visit(CelExpr.Builder root) {
    if (++iterationCount > iterationLimit) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (root.id() == exprIdToReplace) {
      exprIdToReplace = Integer.MIN_VALUE; // Marks that the subtree has been replaced.
      return visit(this.newExpr.setId(root.id()));
    }

    root.setId(celExprIdGenerator.generate(root.id()));

    switch (root.exprKind().getKind()) {
      case SELECT:
        return visit(root, root.select().toBuilder());
      case CALL:
        return visit(root, root.call().toBuilder());
      case CREATE_LIST:
        return visit(root, root.createList().toBuilder());
      case CREATE_STRUCT:
        return visit(root, root.createStruct().toBuilder());
      case CREATE_MAP:
        return visit(root, root.createMap().toBuilder());
      case COMPREHENSION:
        return visit(root, root.comprehension().toBuilder());
      case CONSTANT: // Fall-through is intended
      case IDENT:
      case NOT_SET: // Note: comprehension arguments can contain a not set root.
        return root;
    }
    throw new IllegalArgumentException("unexpected root kind: " + root.exprKind().getKind());
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
      entry.setId(celExprIdGenerator.generate(entry.id()));
      entry.setValue(visit(entry.value().toBuilder()).build());

      createStruct.setEntry(i, entry.build());
    }

    return expr.setCreateStruct(createStruct.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCreateMap.Builder createMap) {
    ImmutableList<CelCreateMap.Entry.Builder> entriesBuilders = createMap.getEntriesBuilders();
    for (int i = 0; i < entriesBuilders.size(); i++) {
      CelCreateMap.Entry.Builder entry = entriesBuilders.get(i);
      entry.setId(celExprIdGenerator.generate(entry.id()));
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

  private MutableExprVisitor(
      ExprIdGenerator celExprIdGenerator,
      CelExpr.Builder newExpr,
      long exprId,
      long iterationLimit) {
    Preconditions.checkState(iterationLimit > 0L);
    this.iterationLimit = iterationLimit;
    this.celExprIdGenerator = celExprIdGenerator;
    this.newExpr = newExpr;
    this.exprIdToReplace = exprId;
  }
}
