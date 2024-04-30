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

package dev.cel.common.ast;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.CelMap;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.CelStruct;

/** CEL expression visitor implementation using Cel native types. */
public class CelExprVisitor {

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

  protected CelExprVisitor() {}

  /** Visit the {@code ast} value, routing to overloads based on the kind of expression. */
  public void visit(CelAbstractSyntaxTree ast) {
    visit(ast.getExpr());
  }

  /** Visit the {@code expr} value, routing to overloads based on the kind of expression. */
  public void visit(CelExpr expr) {
    switch (expr.exprKind().getKind()) {
      case CONSTANT:
        visit(expr, expr.constant());
        break;
      case IDENT:
        visit(expr, expr.ident());
        break;
      case SELECT:
        visit(expr, expr.select());
        break;
      case CALL:
        visit(expr, expr.call());
        break;
      case LIST:
        visit(expr, expr.createList());
        break;
      case STRUCT:
        visit(expr, expr.createStruct());
        break;
      case MAP:
        visit(expr, expr.createMap());
        break;
      case COMPREHENSION:
        visit(expr, expr.comprehension());
        break;
      default:
        throw new IllegalArgumentException("unexpected expr kind: " + expr.exprKind().getKind());
    }
  }

  /** Visit an {@code CelIdent} expression. */
  protected void visit(CelExpr expr, CelIdent ident) {}

  /** Visit a {@code CelConstant} expression. */
  protected void visit(CelExpr expr, CelConstant constant) {}

  /** Visit a {@code CelSelect} expression. */
  protected void visit(CelExpr expr, CelSelect select) {
    visit(select.operand());
  }

  /**
   * Visit a {@code CelCall} expression.
   *
   * <p>Arguments to the call are provided to the {@link #visitArg} function after they have been
   * {@code visit}ed.
   */
  protected void visit(CelExpr expr, CelCall call) {
    if (call.target().isPresent()) {
      visit(call.target().get());
    }
    for (int i = 0; i < call.args().size(); i++) {
      CelExpr arg = call.args().get(i);
      // Visit the argument prior to calling {@code visitArg(expr, arg, i)} which may be used to
      // assist with expression re-writing.
      visit(arg);
      visitArg(expr, arg, i);
    }
  }

  /** Visit a {@code CelStruct} expression. */
  protected void visit(CelExpr expr, CelStruct createStruct) {
    for (CelStruct.Entry entry : createStruct.entries()) {
      visit(entry.value());
    }
  }

  /** Visit a {@code CelMap} expression. */
  protected void visit(CelExpr expr, CelMap createMap) {
    for (CelMap.Entry entry : createMap.entries()) {
      visit(entry.key());
      visit(entry.value());
    }
  }

  /** Visit a {@code CelList} expression. */
  protected void visit(CelExpr expr, CelList createList) {
    for (CelExpr elem : createList.elements()) {
      visit(elem);
    }
  }

  /**
   * Visit a {@code CelComprehension} expression.
   *
   * <p>Arguments to the comprehension are provided to the {@link #visitArg} function after they
   * have been {@code visit}ed.
   */
  protected void visit(CelExpr expr, CelComprehension comprehension) {
    visit(comprehension.iterRange());
    visitArg(expr, comprehension.iterRange(), ComprehensionArg.ITER_RANGE.ordinal());
    visit(comprehension.accuInit());
    visitArg(expr, comprehension.accuInit(), ComprehensionArg.ACCU_INIT.ordinal());
    visit(comprehension.loopCondition());
    visitArg(expr, comprehension.loopCondition(), ComprehensionArg.LOOP_CONDIITON.ordinal());
    visit(comprehension.loopStep());
    visitArg(expr, comprehension.loopStep(), ComprehensionArg.LOOP_STEP.ordinal());
    visit(comprehension.result());
    visitArg(expr, comprehension.result(), ComprehensionArg.RESULT.ordinal());
  }

  /**
   * Visit the argument to an expression in the context of the calling expression.
   *
   * <p>For {@code CelCall} expressions, the arg num refers to the ordinal of the argument.
   *
   * <p>For {@code CelComprehension} expressions, the arg number refers to the ordinal of the enum
   * value as it appears in {@link ComprehensionArg}.
   */
  protected void visitArg(CelExpr expr, CelExpr arg, int argNum) {}
}
