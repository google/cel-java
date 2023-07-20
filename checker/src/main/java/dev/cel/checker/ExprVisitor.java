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

package dev.cel.checker;

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.CreateStruct.Entry.KeyKindCase;
import dev.cel.common.annotations.Internal;

/**
 * CEL expression visitor implementation.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public abstract class ExprVisitor {

  /**
   * ComprehensionArg specifies the arg ordinal values for comprehension arguments, where the
   * ordinal of the enum maps to the ordinal of the argument in the comprehension expression.
   */
  public enum ComprehensionArg {
    ITER_RANGE,
    ACCU_INIT,
    LOOP_CONDIITON,
    LOOP_STEP,
    RESULT
  }

  protected ExprVisitor() {}

  /** Visit the {@code expr} value, routing to overloads based on the kind of expression. */
  public void visit(Expr expr) {
    switch (expr.getExprKindCase()) {
      case CONST_EXPR:
        visit(expr, expr.getConstExpr());
        break;
      case IDENT_EXPR:
        visit(expr, expr.getIdentExpr());
        break;
      case SELECT_EXPR:
        visit(expr, expr.getSelectExpr());
        break;
      case CALL_EXPR:
        visit(expr, expr.getCallExpr());
        break;
      case LIST_EXPR:
        visit(expr, expr.getListExpr());
        break;
      case STRUCT_EXPR:
        visit(expr, expr.getStructExpr());
        break;
      case COMPREHENSION_EXPR:
        visit(expr, expr.getComprehensionExpr());
        break;
      default:
        throw new IllegalArgumentException("unexpected expr kind");
    }
  }

  /** Visit an {@code Expr.Ident} expression. */
  protected void visit(Expr expr, Expr.Ident ident) {}

  /** Visit a {@code Expr.Constant} expression. */
  protected void visit(Expr expr, Constant constant) {}

  /** Visit a {@code Expr.Select} expression. */
  protected void visit(Expr expr, Expr.Select select) {
    visit(select.getOperand());
  }

  /**
   * Visit a {@code Expr.Call} expression.
   *
   * <p>Arguments to the call are provided to the {@link #visitArg} function after they have been
   * {@code visit}ed.
   */
  protected void visit(Expr expr, Expr.Call call) {
    if (call.hasTarget()) {
      visit(call.getTarget());
    }
    for (int i = 0; i < call.getArgsCount(); i++) {
      Expr arg = call.getArgs(i);
      // Visit the argument prior to calling {@code visitArg(expr, arg, i)} which may be used to
      // assist with expression re-writing.
      visit(arg);
      visitArg(expr, arg, i);
    }
  }

  /** Visit a {@code Expr.CreateStruct} expression. */
  protected void visit(Expr expr, Expr.CreateStruct createStruct) {
    for (Expr.CreateStruct.Entry entry : createStruct.getEntriesList()) {
      if (entry.getKeyKindCase() == KeyKindCase.MAP_KEY) {
        visit(entry.getMapKey());
      }
      visit(entry.getValue());
    }
  }

  /** Visit a {@code Expr.CreateList} expression. */
  protected void visit(Expr expr, Expr.CreateList createList) {
    for (Expr elem : createList.getElementsList()) {
      visit(elem);
    }
  }

  /**
   * Visit a {@code Expr.Comprehension} expression.
   *
   * <p>Arguments to the comprehension are provided to the {@link #visitArg} function after they
   * have been {@code visit}ed.
   */
  protected void visit(Expr expr, Expr.Comprehension comprehension) {
    visit(comprehension.getIterRange());
    visitArg(expr, comprehension.getIterRange(), ComprehensionArg.ITER_RANGE.ordinal());
    visit(comprehension.getAccuInit());
    visitArg(expr, comprehension.getAccuInit(), ComprehensionArg.ACCU_INIT.ordinal());
    visit(comprehension.getLoopCondition());
    visitArg(expr, comprehension.getLoopCondition(), ComprehensionArg.LOOP_CONDIITON.ordinal());
    visit(comprehension.getLoopStep());
    visitArg(expr, comprehension.getLoopStep(), ComprehensionArg.LOOP_STEP.ordinal());
    visit(comprehension.getResult());
    visitArg(expr, comprehension.getResult(), ComprehensionArg.RESULT.ordinal());
  }

  /**
   * Visit the argument to an expression in the context of the calling expression.
   *
   * <p>For {@code Expr.Call} expressions, the arg num refers to the ordinal of the argument.
   *
   * <p>For {@code Expr.Comprehension} expressions, the arg number refers to the ordinal of the enum
   * value as it appears in {@link ComprehensionArg}.
   */
  protected void visitArg(Expr expr, Expr arg, int argNum) {}
}
