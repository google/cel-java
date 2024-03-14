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
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExprIdGeneratorFactory.ExprIdGenerator;
import dev.cel.common.navigation.MutableExpr;

import dev.cel.common.navigation.MutableExpr.MutableCall;
import dev.cel.common.navigation.MutableExpr.MutableComprehension;
import dev.cel.common.navigation.MutableExpr.MutableCreateList;
import dev.cel.common.navigation.MutableExpr.MutableCreateMap;
import dev.cel.common.navigation.MutableExpr.MutableCreateStruct;
import dev.cel.common.navigation.MutableExpr.MutableSelect;
import java.util.List;

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
  private final MutableExpr newExpr;
  private final ExprIdGenerator celExprIdGenerator;
  private final long iterationLimit;
  private int iterationCount;
  private long exprIdToReplace;

  static MutableExprVisitor newInstance(
      ExprIdGenerator idGenerator,
      MutableExpr newExpr,
      long exprIdToReplace,
      long iterationLimit) {
    // iterationLimit * 2, because the expr can be walked twice due to the immutable nature of
    // CelExpr.
    return new MutableExprVisitor(idGenerator, newExpr, exprIdToReplace, iterationLimit * 2);
  }

  MutableExpr visit(MutableExpr root) {
    if (++iterationCount > iterationLimit) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (root.id() == exprIdToReplace) {
      exprIdToReplace = Integer.MIN_VALUE; // Marks that the subtree has been replaced.
      this.newExpr.setId(root.id());
      return visit(this.newExpr);
    }

    root.setId(celExprIdGenerator.generate(root.id()));

    switch (root.exprKind()) {
      case SELECT:
        return visit(root, root.select());
      case CALL:
        return visit(root, root.call());
      case CREATE_LIST:
        return visit(root, root.createList());
      case CREATE_STRUCT:
        return visit(root, root.createStruct());
      case CREATE_MAP:
        return visit(root, root.createMap());
      case COMPREHENSION:
        return visit(root, root.comprehension());
      case CONSTANT: // Fall-through is intended
      case IDENT:
      case NOT_SET: // Note: comprehension arguments can contain a not set root.
        return root;
    }
    throw new IllegalArgumentException("unexpected root kind: " + root.exprKind());
  }

  private MutableExpr visit(MutableExpr expr, MutableSelect select) {
    select.setOperand(visit(select.operand()));
    expr.setSelect(select);
    return expr;
  }

  private MutableExpr visit(MutableExpr expr, MutableCall call) {
    if (call.target().isPresent()) {
      call.setTarget(visit(call.target().get()));
    }
    List<MutableExpr> argsBuilders = call.args();
    for (int i = 0; i < argsBuilders.size(); i++) {
      MutableExpr arg = argsBuilders.get(i);
      call.setArg(i, visit(arg));
    }

    expr.setCall(call);
    return expr;
  }

  private MutableExpr visit(MutableExpr expr, MutableCreateStruct createStruct) {
    List<MutableCreateStruct.Entry> entries = createStruct.entries();
    for (int i = 0; i < entries.size(); i++) {
      MutableCreateStruct.Entry entry = entries.get(i);
      entry.setId(celExprIdGenerator.generate(entry.id()));
      entry.setValue(visit(entry.value()));

      createStruct.setEntry(i, entry);
    }

    expr.setCreateStruct(createStruct);
    return expr;
  }

  private MutableExpr visit(MutableExpr expr, MutableCreateMap createMap) {
    List<MutableCreateMap.Entry> entriesBuilders = createMap.entries();
    for (int i = 0; i < entriesBuilders.size(); i++) {
      MutableCreateMap.Entry entry = entriesBuilders.get(i);
      entry.setId(celExprIdGenerator.generate(entry.id()));
      entry.setKey(visit(entry.key()));
      entry.setValue(visit(entry.value()));

      createMap.setEntry(i, entry);
    }

    expr.setCreateMap(createMap);
    return expr;
  }

  private MutableExpr visit(MutableExpr expr, MutableCreateList createList) {
    List<MutableExpr> elementsBuilders = createList.elements();
    for (int i = 0; i < elementsBuilders.size(); i++) {
      MutableExpr elem = elementsBuilders.get(i);
      createList.setElement(i, visit(elem));
    }

    expr.setCreateList(createList);
    return expr;
  }

  private MutableExpr visit(MutableExpr expr, MutableComprehension comprehension) {
    comprehension.setIterRange(visit(comprehension.getIterRange()));
    comprehension.setAccuInit(visit(comprehension.getAccuInit()));
    comprehension.setLoopCondition(visit(comprehension.getLoopCondition()));
    comprehension.setLoopStep(visit(comprehension.getLoopStep()));
    comprehension.setResult(visit(comprehension.getResult()));

    expr.setComprehension(comprehension);
    return expr;
  }

  private MutableExprVisitor(
      ExprIdGenerator celExprIdGenerator,
      MutableExpr newExpr,
      long exprId,
      long iterationLimit) {
    Preconditions.checkState(iterationLimit > 0L);
    this.iterationLimit = iterationLimit;
    this.celExprIdGenerator = celExprIdGenerator;
    this.newExpr = newExpr;
    this.exprIdToReplace = exprId;
  }
}
