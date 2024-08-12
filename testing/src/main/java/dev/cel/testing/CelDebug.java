// Copyright 2022 Google LLC
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

package dev.cel.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import dev.cel.expr.ConstantOrBuilder;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.CreateStruct.EntryOrBuilder;
import dev.cel.expr.ExprOrBuilder;
import java.util.PrimitiveIterator;

/**
 * Utility class providing tools to print a parsed expression graph and adorn each expression
 * element with additional metadata.
 */
public final class CelDebug {

  private static final CelAdorner UNADORNER =
      new CelAdorner() {
        @Override
        public String adorn(ExprOrBuilder expr) {
          return "";
        }

        @Override
        public String adorn(EntryOrBuilder entry) {
          return "";
        }
      };

  /** Returns the unadorned string representation of {@link dev.cel.expr.ExprOrBuilder}. */
  public static String toDebugString(ExprOrBuilder expr) {
    return toAdornedDebugString(expr, UNADORNER);
  }

  /** Returns the adorned string representation of {@link dev.cel.expr.ExprOrBuilder}. */
  public static String toAdornedDebugString(ExprOrBuilder expr, CelAdorner adorner) {
    CelDebug debug = new CelDebug(checkNotNull(adorner));
    debug.appendExpr(checkNotNull(expr));
    return debug.toString();
  }

  private final CelAdorner adorner;
  private final StringBuilder buffer;
  private int indent;
  private boolean lineStart;

  private CelDebug(CelAdorner adorner) {
    this.adorner = adorner;
    buffer = new StringBuilder();
    indent = 0;
    lineStart = false;
  }

  private void appendExpr(ExprOrBuilder expr) {
    switch (expr.getExprKindCase()) {
      case CONST_EXPR:
        appendConst(expr.getConstExpr());
        break;
      case IDENT_EXPR:
        append(expr.getIdentExpr().getName());
        break;
      case SELECT_EXPR:
        appendSelect(expr.getSelectExpr());
        break;
      case CALL_EXPR:
        appendCall(expr.getCallExpr());
        break;
      case LIST_EXPR:
        appendList(expr.getListExpr());
        break;
      case STRUCT_EXPR:
        appendStruct(expr.getStructExpr());
        break;
      case COMPREHENSION_EXPR:
        appendComprehension(expr.getComprehensionExpr());
        break;
      default:
        // A macro expression argument from macro calls will just be an ID and the kind is NOT_SET.
        // To catch this the default must adorn instead of return;
        break;
    }
    adorn(expr);
  }

  private void appendSelect(Expr.SelectOrBuilder selectExpr) {
    appendExpr(selectExpr.getOperand());
    append('.');
    append(selectExpr.getField());
    if (selectExpr.getTestOnly()) {
      append("~test-only~");
    }
  }

  private void appendCall(Expr.CallOrBuilder callExpr) {
    if (callExpr.hasTarget()) {
      appendExpr(callExpr.getTarget());
      append('.');
    }
    append(callExpr.getFunction());
    append('(');
    if (callExpr.getArgsCount() > 0) {
      addIndent();
      appendLine();
      for (int index = 0; index < callExpr.getArgsCount(); index++) {
        if (index > 0) {
          append(',');
          appendLine();
        }
        appendExpr(callExpr.getArgs(index));
      }
      removeIndent();
      appendLine();
    }
    append(')');
  }

  private void appendList(Expr.CreateListOrBuilder listExpr) {
    append('[');
    if (listExpr.getElementsCount() > 0) {
      appendLine();
      addIndent();
      for (int index = 0; index < listExpr.getElementsCount(); index++) {
        if (index > 0) {
          append(',');
          appendLine();
        }
        if (listExpr.getOptionalIndicesList().contains(index)) {
          append("?");
        }
        appendExpr(listExpr.getElements(index));
      }
      removeIndent();
      appendLine();
    }
    append(']');
  }

  private void appendStruct(Expr.CreateStructOrBuilder structExpr) {
    if (structExpr.getMessageName().isEmpty()) {
      appendMap(structExpr);
    } else {
      appendObject(structExpr);
    }
  }

  private void appendObject(Expr.CreateStructOrBuilder structExpr) {
    append(structExpr.getMessageName());
    append('{');
    if (structExpr.getEntriesCount() > 0) {
      appendLine();
      addIndent();
      for (int index = 0; index < structExpr.getEntriesCount(); index++) {
        if (index > 0) {
          append(',');
          appendLine();
        }
        if (structExpr.getEntries(index).getOptionalEntry()) {
          append("?");
        }
        append(structExpr.getEntries(index).getFieldKey());
        append(':');
        appendExpr(structExpr.getEntries(index).getValue());
        adorn(structExpr.getEntries(index));
      }
      removeIndent();
      appendLine();
    }
    append('}');
  }

  private void appendMap(Expr.CreateStructOrBuilder structExpr) {
    append('{');
    if (structExpr.getEntriesCount() > 0) {
      appendLine();
      addIndent();
      for (int index = 0; index < structExpr.getEntriesCount(); index++) {
        if (index > 0) {
          append(',');
          appendLine();
        }
        if (structExpr.getEntries(index).getOptionalEntry()) {
          append("?");
        }
        appendExpr(structExpr.getEntries(index).getMapKey());
        append(':');
        appendExpr(structExpr.getEntries(index).getValue());
        adorn(structExpr.getEntries(index));
      }
      removeIndent();
      appendLine();
    }
    append('}');
  }

  private void appendComprehension(Expr.ComprehensionOrBuilder comprehensionExpr) {
    append("__comprehension__(");
    addIndent();
    appendLine();
    append("// Variable");
    appendLine();
    append(comprehensionExpr.getIterVar());
    append(',');
    appendLine();
    append("// Target");
    appendLine();
    appendExpr(comprehensionExpr.getIterRange());
    append(',');
    appendLine();
    append("// Accumulator");
    appendLine();
    append(comprehensionExpr.getAccuVar());
    append(',');
    appendLine();
    append("// Init");
    appendLine();
    appendExpr(comprehensionExpr.getAccuInit());
    append(',');
    appendLine();
    append("// LoopCondition");
    appendLine();
    appendExpr(comprehensionExpr.getLoopCondition());
    append(',');
    appendLine();
    append("// LoopStep");
    appendLine();
    appendExpr(comprehensionExpr.getLoopStep());
    append(',');
    appendLine();
    append("// Result");
    appendLine();
    appendExpr(comprehensionExpr.getResult());
    append(')');
    removeIndent();
  }

  private void appendConst(ConstantOrBuilder constExpr) {
    switch (constExpr.getConstantKindCase()) {
      case BOOL_VALUE:
        append(constExpr.getBoolValue() ? "true" : "false");
        break;
      case BYTES_VALUE:
        append(String.format("b\"%s\"", constExpr.getBytesValue().toStringUtf8()));
        break;
      case DOUBLE_VALUE:
        {
          String value = String.format("%f", constExpr.getDoubleValue());
          while (value.endsWith("0") && value.charAt(value.length() - 2) != '.') {
            value = value.substring(0, value.length() - 1);
          }
          append(value);
        }
        break;
      case INT64_VALUE:
        append(Long.toString(constExpr.getInt64Value()));
        break;
      case STRING_VALUE:
        {
          append('"');
          PrimitiveIterator.OfInt iterator = constExpr.getStringValue().chars().iterator();
          while (iterator.hasNext()) {
            char codeUnit = (char) iterator.nextInt();
            switch (codeUnit) {
              case '\007': // \a
                append('\\');
                append('a');
                break;
              case '\b':
                append('\\');
                append('b');
                break;
              case '\f':
                append('\\');
                append('f');
                break;
              case '\n':
                append('\\');
                append('n');
                break;
              case '\r':
                append('\\');
                append('r');
                break;
              case '\t':
                append('\\');
                append('t');
                break;
              case '\013': // \v
                append('\\');
                append('v');
                break;
              case '"':
                append('\\');
                append('"');
                break;
              default:
                append(codeUnit);
                break;
            }
          }
          append('"');
        }
        break;
      case UINT64_VALUE:
        append(Long.toUnsignedString(constExpr.getUint64Value()));
        append('u');
        break;
      case NULL_VALUE:
        append("null");
        break;
      default:
        throw new IllegalArgumentException("Unknown constant type");
    }
  }

  @SuppressWarnings("ReturnValueIgnored")
  private void append(char c) {
    appendIndent();
    buffer.append(c);
  }

  @SuppressWarnings("ReturnValueIgnored")
  private void append(String string) {
    appendIndent();
    buffer.append(string);
  }

  @SuppressWarnings("ReturnValueIgnored")
  private void appendLine() {
    buffer.append('\n');
    lineStart = true;
  }

  @SuppressWarnings("ReturnValueIgnored")
  private void appendIndent() {
    if (lineStart) {
      buffer.ensureCapacity(buffer.length() + (indent * 2));
      for (int index = 0; index < indent; index++) {
        buffer.append("  ");
      }
      lineStart = false;
    }
  }

  private void addIndent() {
    checkState(indent >= 0);
    indent++;
  }

  private void removeIndent() {
    checkState(indent > 0);
    indent--;
  }

  private void adorn(ExprOrBuilder expr) {
    append(adorner.adorn(expr));
  }

  private void adorn(EntryOrBuilder entry) {
    append(adorner.adorn(entry));
  }

  @Override
  public String toString() {
    return buffer.toString();
  }
}
