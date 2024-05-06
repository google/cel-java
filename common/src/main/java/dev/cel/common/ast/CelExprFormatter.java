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

import com.google.common.collect.ImmutableSet;

/** Provides string formatting support for {@link CelExpr}. */
final class CelExprFormatter {
  private final StringBuilder indent = new StringBuilder();
  private final StringBuilder exprBuilder = new StringBuilder();

  /** Denotes a set of expression kinds that will not have a new line inserted. */
  private static final ImmutableSet<CelExpr.ExprKind.Kind> EXCLUDED_NEWLINE_KINDS =
      ImmutableSet.of(CelExpr.ExprKind.Kind.CONSTANT, CelExpr.ExprKind.Kind.NOT_SET);

  static String format(CelExpr celExpr) {
    CelExprFormatter formatter = new CelExprFormatter();
    formatter.formatExpr(celExpr);
    return formatter.exprBuilder.toString();
  }

  private void formatExpr(CelExpr celExpr) {
    CelExpr.ExprKind.Kind exprKind = celExpr.exprKind().getKind();
    append(String.format("%s [%d] {", exprKind, celExpr.id()));
    if (!EXCLUDED_NEWLINE_KINDS.contains(exprKind)) {
      appendNewline();
    }

    switch (exprKind) {
      case CONSTANT:
        appendConst(celExpr.constant());
        break;
      case IDENT:
        appendIdent(celExpr.ident());
        break;
      case SELECT:
        appendSelect(celExpr.select());
        break;
      case CALL:
        appendCall(celExpr.call());
        break;
      case LIST:
        appendList(celExpr.list());
        break;
      case STRUCT:
        appendStruct(celExpr.struct());
        break;
      case MAP:
        appendMap(celExpr.map());
        break;
      case COMPREHENSION:
        appendComprehension(celExpr.comprehension());
        break;
      case NOT_SET:
        break;
      default:
        // This should be unreachable unless if we've added any other kinds.
        indent();
        append("Unknown kind: " + exprKind);
        outdent();
        break;
    }

    if (!EXCLUDED_NEWLINE_KINDS.contains(exprKind)) {
      appendNewline();
      append("}");
    } else {
      appendWithoutIndent("}");
    }
  }

  private void appendConst(CelConstant celConstant) {
    appendWithoutIndent(" value: ");
    switch (celConstant.getKind()) {
      case NULL_VALUE:
        appendWithoutIndent("null");
        break;
      case BOOLEAN_VALUE:
        appendWithoutIndent(Boolean.toString(celConstant.booleanValue()));
        break;
      case INT64_VALUE:
        appendWithoutIndent(Long.toString(celConstant.int64Value()));
        break;
      case UINT64_VALUE:
        appendWithoutIndent(celConstant.uint64Value() + "u");
        break;
      case DOUBLE_VALUE:
        appendWithoutIndent(Double.toString(celConstant.doubleValue()));
        break;
      case STRING_VALUE:
        appendWithoutIndent("\"" + celConstant.stringValue() + "\"");
        break;
      case BYTES_VALUE:
        appendWithoutIndent(String.format("b\"%s\"", celConstant.bytesValue().toStringUtf8()));
        break;
      default:
        append("Unknown kind: " + celConstant.getKind());
        break;
    }
    appendWithoutIndent(" ");
  }

  private void appendIdent(CelExpr.CelIdent celIdent) {
    indent();
    append("name: " + celIdent.name());
    outdent();
  }

  private void appendSelect(CelExpr.CelSelect celSelect) {
    indent();
    formatExpr(celSelect.operand());
    outdent();
    appendWithoutIndent(".");
    appendWithoutIndent(celSelect.field());
    if (celSelect.testOnly()) {
      appendWithoutIndent("~presence_test");
    }
  }

  private void appendCall(CelExpr.CelCall celCall) {
    indent();
    appendWithNewline("function: " + celCall.function());
    if (celCall.target().isPresent()) {
      appendWithNewline("target: {");
      indent();
      formatExpr(celCall.target().get());
      outdent();
      appendNewline();
      appendWithNewline("}");
    }
    append("args: {");
    indent();
    for (CelExpr celExpr : celCall.args()) {
      appendNewline();
      formatExpr(celExpr);
    }
    outdent();
    appendNewline();
    append("}");
    outdent();
  }

  private void appendList(CelExpr.CelList celList) {
    indent();
    append("elements: {");
    indent();
    for (CelExpr expr : celList.elements()) {
      appendNewline();
      formatExpr(expr);
    }
    outdent();
    appendNewline();
    append("}");
    if (!celList.optionalIndices().isEmpty()) {
      appendNewline();
      append("optional_indices: [");
      for (int i = 0; i < celList.optionalIndices().size(); i++) {
        appendWithoutIndent(String.valueOf(i));
        if (i != celList.optionalIndices().size() - 1) {
          appendWithoutIndent(", ");
        }
      }
      appendWithoutIndent("]");
    }
    outdent();
  }

  private void appendStruct(CelExpr.CelStruct celStruct) {
    indent();
    appendWithNewline("name: " + celStruct.messageName());
    append("entries: {");
    indent();
    for (CelExpr.CelStruct.Entry entry : celStruct.entries()) {
      appendNewline();
      appendWithNewline(String.format("ENTRY [%d] {", entry.id()));
      indent();
      appendWithNewline("field_key: " + entry.fieldKey());
      if (entry.optionalEntry()) {
        appendWithNewline("optional_entry: true");
      }
      appendWithNewline("value: {");
      indent();
      formatExpr(entry.value());
      outdent();
      appendNewline();
      appendWithNewline("}");
      outdent();
      append("}");
    }
    outdent();
    appendNewline();
    append("}");
    outdent();
  }

  private void appendMap(CelExpr.CelMap celMap) {
    indent();
    boolean firstLine = true;
    for (CelExpr.CelMap.Entry entry : celMap.entries()) {
      if (!firstLine) {
        appendNewline();
      } else {
        firstLine = false;
      }
      appendWithNewline(String.format("MAP_ENTRY [%d] {", entry.id()));
      indent();
      appendWithNewline("key: {");
      indent();
      formatExpr(entry.key());
      outdent();
      appendNewline();
      appendWithNewline("}");
      if (entry.optionalEntry()) {
        appendWithNewline("optional_entry: true");
      }
      appendWithNewline("value: {");
      indent();
      formatExpr(entry.value());
      outdent();
      appendNewline();
      appendWithNewline("}");
      outdent();
      append("}");
    }
    outdent();
  }

  private void appendComprehension(CelExpr.CelComprehension celComprehension) {
    indent();
    appendWithNewline("iter_var: " + celComprehension.iterVar());
    // Iter range
    appendWithNewline("iter_range: {");
    indent();
    formatExpr(celComprehension.iterRange());
    outdent();
    appendNewline();
    appendWithNewline("}");

    appendWithNewline("accu_var: " + celComprehension.accuVar());
    // Accu init
    appendWithNewline("accu_init: {");
    indent();
    formatExpr(celComprehension.accuInit());
    outdent();
    appendNewline();
    appendWithNewline("}");

    // Loop condition
    appendWithNewline("loop_condition: {");
    indent();
    formatExpr(celComprehension.loopCondition());
    outdent();
    appendNewline();
    appendWithNewline("}");

    // Loop step
    appendWithNewline("loop_step: {");
    indent();
    formatExpr(celComprehension.loopStep());
    outdent();
    appendNewline();
    appendWithNewline("}");

    // Result
    appendWithNewline("result: {");
    indent();
    formatExpr(celComprehension.result());
    outdent();
    appendNewline();
    append("}");

    outdent();
  }

  private void append(String str) {
    exprBuilder.append(indent);
    exprBuilder.append(str);
  }

  private void appendWithNewline(String str) {
    append(str);
    appendNewline();
  }

  private void appendWithoutIndent(String str) {
    exprBuilder.append(str);
  }

  private void appendNewline() {
    exprBuilder.append('\n');
  }

  private void indent() {
    indent.append("  ");
  }

  private void outdent() {
    indent.setLength(indent.length() - 2);
  }

  private CelExprFormatter() {}
}
