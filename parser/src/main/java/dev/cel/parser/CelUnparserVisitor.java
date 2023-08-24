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
package dev.cel.parser;

import com.google.protobuf.ByteString;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelExprVisitor;
import java.util.Optional;

/** Visitor implementation to unparse an AST. */
public class CelUnparserVisitor extends CelExprVisitor {
  protected static final String LEFT_PAREN = "(";
  protected static final String RIGHT_PAREN = ")";
  protected static final String DOT = ".";
  protected static final String COMMA = ",";
  protected static final String SPACE = " ";
  protected static final String LEFT_BRACKET = "[";
  protected static final String RIGHT_BRACKET = "]";
  protected static final String LEFT_BRACE = "{";
  protected static final String RIGHT_BRACE = "}";
  protected static final String COLON = ":";
  protected static final String QUESTION_MARK = "?";

  protected final CelAbstractSyntaxTree ast;
  protected final CelSource sourceInfo;
  protected final StringBuilder stringBuilder;

  /** Creates a new {@link CelUnparserVisitor}. */
  public CelUnparserVisitor(CelAbstractSyntaxTree ast) {
    this.ast = ast;
    this.sourceInfo = ast.getSource();
    this.stringBuilder = new StringBuilder();
  }

  public String unparse() {
    visit(ast.getExpr());
    return stringBuilder.toString();
  }

  @Override
  public void visit(CelExpr expr) {
    if (sourceInfo.getMacroCalls().containsKey(expr.id())) {
      visit(sourceInfo.getMacroCalls().get(expr.id()));
      return;
    }
    super.visit(expr);
  }

  @Override
  protected void visit(CelExpr expr, CelConstant constant) {
    switch (constant.getKind()) {
      case STRING_VALUE:
        stringBuilder.append("\"").append(constant.stringValue()).append("\"");
        break;
      case INT64_VALUE:
        stringBuilder.append(constant.int64Value());
        break;
      case UINT64_VALUE:
        stringBuilder.append(constant.uint64Value()).append("u");
        break;
      case BOOLEAN_VALUE:
        stringBuilder.append(constant.booleanValue() ? "true" : "false");
        break;
      case DOUBLE_VALUE:
        stringBuilder.append(constant.doubleValue());
        break;
      case NULL_VALUE:
        stringBuilder.append("null");
        break;
      case BYTES_VALUE:
        stringBuilder.append("b\"").append(bytesToOctets(constant.bytesValue())).append("\"");
        break;
      default:
        throw new IllegalArgumentException("unexpected expr kind");
    }
  }

  @Override
  protected void visit(CelExpr expr, CelIdent ident) {
    stringBuilder.append(ident.name());
  }

  @Override
  protected void visit(CelExpr expr, CelSelect select) {
    if (select.testOnly()) {
      stringBuilder.append(Operator.HAS.getFunction()).append(LEFT_PAREN);
    }
    CelExpr operand = select.operand();
    boolean nested = !select.testOnly() && isBinaryOrTernaryOperator(operand);
    visitMaybeNested(operand, nested);
    stringBuilder.append(DOT).append(select.field());
    if (select.testOnly()) {
      stringBuilder.append(RIGHT_PAREN);
    }
  }

  @Override
  protected void visit(CelExpr expr, CelCall call) {
    String fun = call.function();

    Optional<String> op = Operator.lookupUnaryOperator(fun);
    if (op.isPresent()) {
      visitUnary(call, op.get());
      return;
    }

    op = Operator.lookupBinaryOperator(fun);
    if (op.isPresent()) {
      visitBinary(call, op.get());
      return;
    }

    if (fun.equals(Operator.INDEX.getFunction())) {
      visitIndex(call);
      return;
    }

    if (fun.equals(Operator.CONDITIONAL.getFunction())) {
      visitTernary(call);
      return;
    }

    if (call.target().isPresent()) {
      boolean nested = isBinaryOrTernaryOperator(call.target().get());
      visitMaybeNested(call.target().get(), nested);
      stringBuilder.append(DOT);
    }

    stringBuilder.append(fun).append(LEFT_PAREN);
    for (int i = 0; i < call.args().size(); i++) {
      if (i > 0) {
        stringBuilder.append(COMMA).append(SPACE);
      }
      visit(call.args().get(i));
    }
    stringBuilder.append(RIGHT_PAREN);
  }

  @Override
  protected void visit(CelExpr expr, CelCreateList createList) {
    stringBuilder.append(LEFT_BRACKET);
    for (int i = 0; i < createList.elements().size(); i++) {
      if (i > 0) {
        stringBuilder.append(COMMA).append(SPACE);
      }
      visit(createList.elements().get(i));
    }
    stringBuilder.append(RIGHT_BRACKET);
  }

  @Override
  protected void visit(CelExpr expr, CelCreateStruct createStruct) {
    stringBuilder.append(createStruct.messageName());
    stringBuilder.append(LEFT_BRACE);
    for (int i = 0; i < createStruct.entries().size(); i++) {
      if (i > 0) {
        stringBuilder.append(COMMA).append(SPACE);
      }

      CelCreateStruct.Entry e = createStruct.entries().get(i);
      stringBuilder.append(e.fieldKey());
      stringBuilder.append(COLON).append(SPACE);
      visit(e.value());
    }
    stringBuilder.append(RIGHT_BRACE);
  }

  @Override
  protected void visit(CelExpr expr, CelCreateMap createMap) {
    stringBuilder.append(LEFT_BRACE);
    for (int i = 0; i < createMap.entries().size(); i++) {
      if (i > 0) {
        stringBuilder.append(COMMA).append(SPACE);
      }

      CelCreateMap.Entry e = createMap.entries().get(i);
      visit(e.key());
      stringBuilder.append(COLON).append(SPACE);
      visit(e.value());
    }
    stringBuilder.append(RIGHT_BRACE);
  }

  @Override
  protected void visit(CelExpr expr, CelComprehension comprehension) {
    throw new UnsupportedOperationException(
        "Comprehension unparsing requires macro calls to be populated. Ensure the option is"
            + " enabled.");
  }

  private void visitUnary(CelCall expr, String op) {
    if (expr.args().size() != 1) {
      throw new IllegalArgumentException(String.format("unexpected unary: %s", expr));
    }
    stringBuilder.append(op);
    boolean nested = isComplexOperator(expr.args().get(0));
    visitMaybeNested(expr.args().get(0), nested);
  }

  private void visitBinary(CelCall expr, String op) {
    if (expr.args().size() != 2) {
      throw new IllegalArgumentException(String.format("unexpected binary: %s", expr));
    }

    CelExpr lhs = expr.args().get(0);
    CelExpr rhs = expr.args().get(1);
    String fun = expr.function();

    // add parens if the current operator is lower precedence than the lhs expr
    // operator.
    boolean lhsParen = isComplexOperatorWithRespectTo(lhs, fun);
    // add parens if the current operator is lower precedence than the rhs expr
    // operator, or the same precedence and the operator is left recursive.
    boolean rhsParen = isComplexOperatorWithRespectTo(rhs, fun);
    if (!rhsParen && Operator.isOperatorLeftRecursive(fun)) {
      rhsParen = isOperatorSamePrecedence(fun, rhs);
    }

    visitMaybeNested(lhs, lhsParen);
    stringBuilder.append(SPACE).append(op).append(SPACE);
    visitMaybeNested(rhs, rhsParen);
  }

  private void visitTernary(CelCall expr) {
    if (expr.args().size() != 3) {
      throw new IllegalArgumentException(String.format("unexpected ternary: %s", expr));
    }

    boolean nested =
        isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr.args().get(0))
            || isComplexOperator(expr.args().get(0));
    visitMaybeNested(expr.args().get(0), nested);

    stringBuilder.append(SPACE).append(QUESTION_MARK).append(SPACE);

    nested =
        isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr.args().get(1))
            || isComplexOperator(expr.args().get(1));
    visitMaybeNested(expr.args().get(1), nested);

    stringBuilder.append(SPACE).append(COLON).append(SPACE);

    nested =
        isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr.args().get(2))
            || isComplexOperator(expr.args().get(2));
    visitMaybeNested(expr.args().get(2), nested);
  }

  private void visitIndex(CelCall expr) {
    if (expr.args().size() != 2) {
      throw new IllegalArgumentException(String.format("unexpected index call: %s", expr));
    }
    boolean nested = isBinaryOrTernaryOperator(expr.args().get(0));
    visitMaybeNested(expr.args().get(0), nested);
    stringBuilder.append(LEFT_BRACKET);
    visit(expr.args().get(1));
    stringBuilder.append(RIGHT_BRACKET);
  }

  private void visitMaybeNested(CelExpr expr, boolean nested) {
    if (nested) {
      stringBuilder.append(LEFT_PAREN);
    }
    visit(expr);
    if (nested) {
      stringBuilder.append(RIGHT_PAREN);
    }
  }

  private boolean isBinaryOrTernaryOperator(CelExpr expr) {
    if (!isComplexOperator(expr)) {
      return false;
    }
    return Operator.lookupBinaryOperator(expr.call().function()).isPresent()
        || isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr);
  }

  private boolean isComplexOperator(CelExpr expr) {
    // If the arg is a call with more than one arg, return true
    return expr.exprKind().getKind().equals(Kind.CALL) && expr.call().args().size() >= 2;
  }

  private boolean isOperatorSamePrecedence(String op, CelExpr expr) {
    if (!expr.exprKind().getKind().equals(Kind.CALL)) {
      return false;
    }
    return Operator.lookupPrecedence(op) == Operator.lookupPrecedence(expr.call().function());
  }

  private boolean isComplexOperatorWithRespectTo(CelExpr expr, String op) {
    // If the arg is not a call with more than one arg, return false.
    if (!expr.exprKind().getKind().equals(Kind.CALL) || expr.call().args().size() < 2) {
      return false;
    }
    // Otherwise, return whether the given op has lower precedence than expr
    return Operator.isOperatorLowerPrecedence(op, expr);
  }

  // bytesToOctets converts byte sequences to a string using a three digit octal encoded value
  // per byte.
  private String bytesToOctets(ByteString bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes.toByteArray()) {
      sb.append(String.format("\\%03o", b));
    }
    return sb.toString();
  }
}
