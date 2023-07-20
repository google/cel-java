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

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.CreateStruct.Entry;
import dev.cel.expr.Expr.ExprKindCase;
import dev.cel.expr.ParsedExpr;
import dev.cel.expr.SourceInfo;
import com.google.protobuf.ByteString;
import dev.cel.checker.ExprVisitor;
import java.util.Optional;

/** Unparser implementation for CEL. */
final class CelUnparserImpl implements CelUnparser {

  private static final String LEFT_PAREN = "(";
  private static final String RIGHT_PAREN = ")";
  private static final String DOT = ".";
  private static final String COMMA = ",";
  private static final String SPACE = " ";
  private static final String LEFT_BRACKET = "[";
  private static final String RIGHT_BRACKET = "]";
  private static final String LEFT_BRACCE = "{";
  private static final String RIGHT_BRACE = "}";
  private static final String COLON = ":";
  private static final String QUESTION_MARK = "?";

  @Override
  public String unparse(ParsedExpr parsedExpr) {
    return CelUnparserExprVisitor.unparse(parsedExpr);
  }

  static final class CelUnparserExprVisitor extends ExprVisitor {
    private final Expr expr;
    private final SourceInfo sourceInfo;
    private final StringBuilder stringBuilder;

    /** Creates a new {@link CelUnparserExprVisitor}. */
    public CelUnparserExprVisitor(Expr expr, SourceInfo sourceInfo) {
      this.expr = expr;
      this.sourceInfo = sourceInfo;
      this.stringBuilder = new StringBuilder();
    }

    public static String unparse(ParsedExpr parsedExpr) {
      CelUnparserExprVisitor unparserVisitor =
          new CelUnparserExprVisitor(parsedExpr.getExpr(), parsedExpr.getSourceInfo());
      return unparserVisitor.doParse();
    }

    private String doParse() {
      visit(expr);
      return stringBuilder.toString();
    }

    @Override
    public void visit(Expr expr) {
      if (sourceInfo.getMacroCallsMap().containsKey(expr.getId())) {
        visit(sourceInfo.getMacroCallsMap().get(expr.getId()));
        return;
      }
      super.visit(expr);
    }

    @Override
    protected void visit(Expr expr, Constant constant) {
      switch (constant.getConstantKindCase()) {
        case STRING_VALUE:
          stringBuilder.append("\"").append(constant.getStringValue()).append("\"");
          break;
        case INT64_VALUE:
          stringBuilder.append(constant.getInt64Value());
          break;
        case UINT64_VALUE:
          stringBuilder.append(constant.getUint64Value()).append("u");
          break;
        case BOOL_VALUE:
          stringBuilder.append(constant.getBoolValue() ? "true" : "false");
          break;
        case DOUBLE_VALUE:
          stringBuilder.append(constant.getDoubleValue());
          break;
        case NULL_VALUE:
          stringBuilder.append("null");
          break;
        case BYTES_VALUE:
          stringBuilder.append("b\"").append(bytesToOctets(constant.getBytesValue())).append("\"");
          break;
        default:
          throw new IllegalArgumentException("unexpected expr kind");
      }
    }

    @Override
    protected void visit(Expr expr, Expr.Ident ident) {
      stringBuilder.append(ident.getName());
    }

    @Override
    protected void visit(Expr expr, Expr.Select select) {
      if (select.getTestOnly()) {
        stringBuilder.append(Operator.HAS.getFunction()).append(LEFT_PAREN);
      }
      Expr operand = select.getOperand();
      boolean nested = !select.getTestOnly() && isBinaryOrTernaryOperator(operand);
      visitMaybeNested(operand, nested);
      stringBuilder.append(DOT).append(select.getField());
      if (select.getTestOnly()) {
        stringBuilder.append(RIGHT_PAREN);
      }
    }

    @Override
    protected void visit(Expr expr, Expr.Call call) {
      String fun = call.getFunction();

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

      if (call.hasTarget()) {
        boolean nested = isBinaryOrTernaryOperator(call.getTarget());
        visitMaybeNested(call.getTarget(), nested);
        stringBuilder.append(DOT);
      }

      stringBuilder.append(fun).append(LEFT_PAREN);
      for (int i = 0; i < call.getArgsCount(); i++) {
        if (i > 0) {
          stringBuilder.append(COMMA).append(SPACE);
        }
        visit(call.getArgs(i));
      }
      stringBuilder.append(RIGHT_PAREN);
    }

    @Override
    protected void visit(Expr expr, Expr.CreateList createList) {
      stringBuilder.append(LEFT_BRACKET);
      for (int i = 0; i < createList.getElementsCount(); i++) {
        if (i > 0) {
          stringBuilder.append(COMMA).append(SPACE);
        }
        visit(createList.getElements(i));
      }
      stringBuilder.append(RIGHT_BRACKET);
    }

    @Override
    protected void visit(Expr expr, Expr.CreateStruct createStruct) {
      if (!createStruct.getMessageName().isEmpty()) {
        stringBuilder.append(createStruct.getMessageName());
      }
      stringBuilder.append(LEFT_BRACCE);
      for (int i = 0; i < createStruct.getEntriesCount(); i++) {
        if (i > 0) {
          stringBuilder.append(COMMA).append(SPACE);
        }

        Entry e = createStruct.getEntries(i);
        switch (e.getKeyKindCase()) {
          case FIELD_KEY:
            stringBuilder.append(e.getFieldKey());
            break;
          case MAP_KEY:
            visit(e.getMapKey());
            break;
          default:
            throw new IllegalArgumentException(
                String.format("unexpected struct: %s", createStruct));
        }
        stringBuilder.append(COLON).append(SPACE);
        visit(e.getValue());
      }
      stringBuilder.append(RIGHT_BRACE);
    }

    // TODO: comprehension unparsing should use macro call metadata
    @Override
    protected void visit(Expr expr, Expr.Comprehension comprehension) {
      boolean nested = isComplexOperator(comprehension.getIterRange());
      visitMaybeNested(comprehension.getIterRange(), nested);
      stringBuilder.append(DOT);

      if (comprehension
          .getLoopStep()
          .getCallExpr()
          .getFunction()
          .equals(Operator.LOGICAL_AND.getFunction())) {
        visitAllMacro(comprehension);
        return;
      }

      if (comprehension
          .getLoopStep()
          .getCallExpr()
          .getFunction()
          .equals(Operator.LOGICAL_OR.getFunction())) {
        visitExistsMacro(comprehension);
        return;
      }

      if (comprehension.getResult().getExprKindCase() == ExprKindCase.CALL_EXPR) {
        visitExistsOneMacro(comprehension);
        return;
      }

      visitMapMacro(comprehension);
    }

    private void visitUnary(Expr.Call expr, String op) {
      if (expr.getArgsCount() != 1) {
        throw new IllegalArgumentException(String.format("unexpected unary: %s", expr));
      }
      stringBuilder.append(op);
      boolean nested = isComplexOperator(expr.getArgs(0));
      visitMaybeNested(expr.getArgs(0), nested);
    }

    private void visitBinary(Expr.Call expr, String op) {
      if (expr.getArgsCount() != 2) {
        throw new IllegalArgumentException(String.format("unexpected binary: %s", expr));
      }

      Expr lhs = expr.getArgs(0);
      Expr rhs = expr.getArgs(1);
      String fun = expr.getFunction();

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

    private void visitTernary(Expr.Call expr) {
      if (expr.getArgsCount() != 3) {
        throw new IllegalArgumentException(String.format("unexpected ternary: %s", expr));
      }

      boolean nested =
          isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr.getArgs(0))
              || isComplexOperator(expr.getArgs(0));
      visitMaybeNested(expr.getArgs(0), nested);

      stringBuilder.append(SPACE).append(QUESTION_MARK).append(SPACE);

      nested =
          isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr.getArgs(1))
              || isComplexOperator(expr.getArgs(1));
      visitMaybeNested(expr.getArgs(1), nested);

      stringBuilder.append(SPACE).append(COLON).append(SPACE);

      nested =
          isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr.getArgs(2))
              || isComplexOperator(expr.getArgs(2));
      visitMaybeNested(expr.getArgs(2), nested);
    }

    private void visitIndex(Expr.Call expr) {
      if (expr.getArgsCount() != 2) {
        throw new IllegalArgumentException(String.format("unexpected index call: %s", expr));
      }
      boolean nested = isBinaryOrTernaryOperator(expr.getArgs(0));
      visitMaybeNested(expr.getArgs(0), nested);
      stringBuilder.append(LEFT_BRACKET);
      visit(expr.getArgs(1));
      stringBuilder.append(RIGHT_BRACKET);
    }

    private void visitMaybeNested(Expr expr, boolean nested) {
      if (nested) {
        stringBuilder.append(LEFT_PAREN);
      }
      visit(expr);
      if (nested) {
        stringBuilder.append(RIGHT_PAREN);
      }
    }

    private void visitAllMacro(Expr.Comprehension expr) {
      if (expr.getLoopStep().getCallExpr().getArgsCount() != 2) {
        throw new IllegalArgumentException(String.format("unexpected all macro: %s", expr));
      }
      stringBuilder
          .append(Operator.ALL.getFunction())
          .append(LEFT_PAREN)
          .append(expr.getIterVar())
          .append(COMMA)
          .append(SPACE);
      visit(expr.getLoopStep().getCallExpr().getArgs(1));
      stringBuilder.append(RIGHT_PAREN);
    }

    private void visitExistsMacro(Expr.Comprehension expr) {
      if (expr.getLoopStep().getCallExpr().getArgsCount() != 2) {
        throw new IllegalArgumentException(String.format("unexpected exists macro %s", expr));
      }
      stringBuilder
          .append(Operator.EXISTS.getFunction())
          .append(LEFT_PAREN)
          .append(expr.getIterVar())
          .append(COMMA)
          .append(SPACE);
      visit(expr.getLoopStep().getCallExpr().getArgs(1));
      stringBuilder.append(RIGHT_PAREN);
    }

    private void visitExistsOneMacro(Expr.Comprehension expr) {
      if (expr.getLoopStep().getCallExpr().getArgsCount() != 3) {
        throw new IllegalArgumentException(String.format("unexpected exists one macro: %s", expr));
      }
      stringBuilder
          .append(Operator.EXISTS_ONE.getFunction())
          .append(LEFT_PAREN)
          .append(expr.getIterVar())
          .append(COMMA)
          .append(SPACE);
      visit(expr.getLoopStep().getCallExpr().getArgs(0));
      stringBuilder.append(RIGHT_PAREN);
    }

    private void visitMapMacro(Expr.Comprehension expr) {
      stringBuilder
          .append(Operator.MAP.getFunction())
          .append(LEFT_PAREN)
          .append(expr.getIterVar())
          .append(COMMA)
          .append(SPACE);
      Expr step = expr.getLoopStep();
      if (step.getCallExpr().getFunction().equals(Operator.CONDITIONAL.getFunction())) {
        if (step.getCallExpr().getArgsCount() != 3) {
          throw new IllegalArgumentException(
              String.format("unexpected exists map macro filter step: %s", expr));
        }
        visit(step.getCallExpr().getArgs(0));
        stringBuilder.append(COMMA).append(SPACE);

        Expr temp = step.getCallExpr().getArgs(1);
        step = temp;
      }

      if (step.getCallExpr().getArgsCount() != 2
          || step.getCallExpr().getArgs(1).getListExpr().getElementsCount() != 1) {
        throw new IllegalArgumentException(String.format("unexpected exists map macro: %s", expr));
      }
      visit(step.getCallExpr().getArgs(1).getListExpr().getElements(0));
      stringBuilder.append(RIGHT_PAREN);
    }

    private boolean isBinaryOrTernaryOperator(Expr expr) {
      if (!isComplexOperator(expr)) {
        return false;
      }
      return Operator.lookupBinaryOperator(expr.getCallExpr().getFunction()).isPresent()
          || isOperatorSamePrecedence(Operator.CONDITIONAL.getFunction(), expr);
    }

    private boolean isComplexOperator(Expr expr) {
      // If the arg is a call with more than one arg, return true
      return expr.hasCallExpr() && expr.getCallExpr().getArgsCount() >= 2;
    }

    private boolean isOperatorSamePrecedence(String op, Expr expr) {
      if (!expr.hasCallExpr()) {
        return false;
      }
      return Operator.lookupPrecedence(op)
          == Operator.lookupPrecedence(expr.getCallExpr().getFunction());
    }

    private boolean isComplexOperatorWithRespectTo(Expr expr, String op) {
      // If the arg is not a call with more than one arg, return false.
      if (!expr.hasCallExpr() || expr.getCallExpr().getArgsCount() < 2) {
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
}
