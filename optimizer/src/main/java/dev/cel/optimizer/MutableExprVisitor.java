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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelExprIdGeneratorFactory.ExprIdGenerator;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExpr.CelMutableList;
import dev.cel.common.ast.CelMutableExpr.CelMutableMap;
import dev.cel.common.ast.CelMutableExpr.CelMutableSelect;
import dev.cel.common.ast.CelMutableExpr.CelMutableStruct;
import java.util.List;

/**
 * MutableExprVisitor performs mutation of {@link CelMutableExpr} based on its configured
 * parameters.
 *
 * <p>This class is NOT thread-safe. Callers should spawn a new instance of this class each time the
 * expression is being mutated.
 */
@Internal
final class MutableExprVisitor {
  private final CelMutableExpr newExpr;
  private final ExprIdGenerator celExprIdGenerator;
  private final long iterationLimit;
  private int iterationCount;
  private long exprIdToReplace;

  static MutableExprVisitor newInstance(
      ExprIdGenerator idGenerator,
      CelMutableExpr newExpr,
      long exprIdToReplace,
      long iterationLimit) {
    // iterationLimit * 2, because the expr can be walked twice due to the immutable nature of
    // CelExpr.
    return new MutableExprVisitor(idGenerator, newExpr, exprIdToReplace, iterationLimit * 2);
  }

  CelMutableExpr visit(CelMutableExpr root) {
    if (++iterationCount > iterationLimit) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (root.id() == exprIdToReplace) {
      exprIdToReplace = Integer.MIN_VALUE; // Marks that the subtree has been replaced.
      this.newExpr.setId(root.id());
      return visit(this.newExpr);
    }

    root.setId(celExprIdGenerator.generate(root.id()));

    switch (root.getKind()) {
      case SELECT:
        return visit(root, root.select());
      case CALL:
        return visit(root, root.call());
      case LIST:
        return visit(root, root.createList());
      case STRUCT:
        return visit(root, root.createStruct());
      case MAP:
        return visit(root, root.createMap());
      case COMPREHENSION:
        return visit(root, root.comprehension());
      case CONSTANT: // Fall-through is intended
      case IDENT:
      case NOT_SET: // Note: comprehension arguments can contain a not set root.
        return root;
    }
    throw new IllegalArgumentException("unexpected root kind: " + root.getKind());
  }

  @CanIgnoreReturnValue
  private CelMutableExpr visit(CelMutableExpr expr, CelMutableSelect select) {
    select.setOperand(visit(select.operand()));
    return expr;
  }

  @CanIgnoreReturnValue
  private CelMutableExpr visit(CelMutableExpr expr, CelMutableCall call) {
    if (call.target().isPresent()) {
      call.setTarget(visit(call.target().get()));
    }
    List<CelMutableExpr> argsBuilders = call.args();
    for (int i = 0; i < argsBuilders.size(); i++) {
      CelMutableExpr arg = argsBuilders.get(i);
      call.setArg(i, visit(arg));
    }

    return expr;
  }

  @CanIgnoreReturnValue
  private CelMutableExpr visit(CelMutableExpr expr, CelMutableStruct createStruct) {
    List<CelMutableStruct.Entry> entries = createStruct.entries();
    for (CelMutableStruct.Entry entry : entries) {
      entry.setId(celExprIdGenerator.generate(entry.id()));
      entry.setValue(visit(entry.value()));
    }

    return expr;
  }

  @CanIgnoreReturnValue
  private CelMutableExpr visit(CelMutableExpr expr, CelMutableMap createMap) {
    List<CelMutableMap.Entry> entriesBuilders = createMap.entries();
    for (CelMutableMap.Entry entry : entriesBuilders) {
      entry.setId(celExprIdGenerator.generate(entry.id()));
      entry.setKey(visit(entry.key()));
      entry.setValue(visit(entry.value()));
    }

    return expr;
  }

  @CanIgnoreReturnValue
  private CelMutableExpr visit(CelMutableExpr expr, CelMutableList createList) {
    List<CelMutableExpr> elementsBuilders = createList.elements();
    for (int i = 0; i < elementsBuilders.size(); i++) {
      CelMutableExpr elem = elementsBuilders.get(i);
      createList.setElement(i, visit(elem));
    }

    return expr;
  }

  @CanIgnoreReturnValue
  private CelMutableExpr visit(CelMutableExpr expr, CelMutableComprehension comprehension) {
    comprehension.setIterRange(visit(comprehension.iterRange()));
    comprehension.setAccuInit(visit(comprehension.accuInit()));
    comprehension.setLoopCondition(visit(comprehension.loopCondition()));
    comprehension.setLoopStep(visit(comprehension.loopStep()));
    comprehension.setResult(visit(comprehension.result()));

    return expr;
  }

  private MutableExprVisitor(
      ExprIdGenerator celExprIdGenerator,
      CelMutableExpr newExpr,
      long exprId,
      long iterationLimit) {
    Preconditions.checkState(iterationLimit > 0L);
    this.iterationLimit = iterationLimit;
    this.celExprIdGenerator = celExprIdGenerator;
    this.newExpr = newExpr;
    this.exprIdToReplace = exprId;
  }
}
