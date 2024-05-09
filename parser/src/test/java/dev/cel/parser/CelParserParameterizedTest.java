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

package dev.cel.parser;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.ExprOrBuilder;
import dev.cel.expr.ParsedExpr;
import dev.cel.expr.SourceInfo;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.testing.BaselineTestCase;
import dev.cel.testing.CelAdorner;
import dev.cel.testing.CelDebug;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Invokes parser tests and compares their output against baseline files. */
@RunWith(TestParameterInjector.class)
public final class CelParserParameterizedTest extends BaselineTestCase {
  private static final CelParser PARSER =
      CelParserFactory.standardCelParserBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addLibraries(CelOptionalLibrary.INSTANCE)
          .addMacros(
              CelMacro.newGlobalVarArgMacro("noop_macro", (a, b, c) -> Optional.empty()),
              CelMacro.newGlobalMacro(
                  "get_constant_macro",
                  0,
                  (a, b, c) ->
                      Optional.of(
                          CelExpr.newBuilder()
                              .setId(1)
                              .setConstant(CelConstant.ofValue(10L))
                              .build())))
          .setOptions(CelOptions.current().populateMacroCalls(true).build())
          .build();

  @Test
  public void parser() {
    runTest(PARSER, "x * 2");
    runTest(PARSER, "x * 2u");
    runTest(PARSER, "x * 2.0");
    runTest(PARSER, "\"\\u2764\"");
    runTest(PARSER, "\"\u2764\"");
    runTest(PARSER, "! false");
    runTest(PARSER, "-a");
    runTest(PARSER, "a.b(5)");
    runTest(PARSER, "a[3]");
    runTest(PARSER, "SomeMessage{foo: 5, bar: \"xyz\"}");
    runTest(PARSER, "[3, 4, 5]");
    runTest(PARSER, "{foo: 5, bar: \"xyz\"}");
    runTest(PARSER, "a > 5 && a < 10");
    runTest(PARSER, "a < 5 || a > 10");
    runTest(PARSER, "\"abc\" + \"def\"");
    runTest(PARSER, "\"A\"");
    runTest(PARSER, "true");
    runTest(PARSER, "false");
    runTest(PARSER, "0");
    runTest(PARSER, "42");
    runTest(PARSER, "0u");
    runTest(PARSER, "23u");
    runTest(PARSER, "24u");
    runTest(PARSER, "0xAu");
    runTest(PARSER, "-0xA");
    runTest(PARSER, "0xA");
    runTest(PARSER, "-1");
    runTest(PARSER, "4--4");
    runTest(PARSER, "4--4.1");
    runTest(PARSER, "b\"abc\"");
    runTest(PARSER, "23.39");
    runTest(PARSER, "!a");
    runTest(PARSER, "null");
    runTest(PARSER, "a");
    runTest(PARSER, "a?b:c");
    runTest(PARSER, "a || b");
    runTest(PARSER, "a || b || c || d || e || f ");
    runTest(PARSER, "a && b");
    runTest(PARSER, "a && b && c && d && e && f && g");
    runTest(PARSER, "a && b && c && d || e && f && g && h");
    runTest(PARSER, "a + b");
    runTest(PARSER, "a - b");
    runTest(PARSER, "a * b");
    runTest(PARSER, "a / b");
    runTest(PARSER, "a % b");
    runTest(PARSER, "a in b");
    runTest(PARSER, "a == b");
    runTest(PARSER, "a != b");
    runTest(PARSER, "a > b");
    runTest(PARSER, "a >= b");
    runTest(PARSER, "a < b");
    runTest(PARSER, "a <= b");
    runTest(PARSER, "a.b");
    runTest(PARSER, "a.b.c");
    runTest(PARSER, "a[b]");
    runTest(PARSER, "foo{ }");
    runTest(PARSER, "foo{ a:b }");
    runTest(PARSER, "foo{ a:b, c:d }");
    runTest(PARSER, "{}");
    runTest(PARSER, "{a:b, c:d}");
    runTest(PARSER, "[]");
    runTest(PARSER, "[a]");
    runTest(PARSER, "[a, b, c]");
    runTest(PARSER, "(a)");
    runTest(PARSER, "((a))");
    runTest(PARSER, "a()");
    runTest(PARSER, "a(b)");
    runTest(PARSER, "a(b, c)");
    runTest(PARSER, "a.b()");
    runTest(PARSER, "a.b(c)");
    runTest(PARSER, "aaa.bbb(ccc)");
    runTest(PARSER, "has(m.f)");
    runTest(PARSER, "m.exists_one(v, f)");
    runTest(PARSER, "m.map(v, f)");
    runTest(PARSER, "m.map(v, p, f)");
    runTest(PARSER, "m.filter(v, p)");
    runTest(PARSER, "[] + [1,2,3,] + [4]");
    runTest(PARSER, "{1:2u, 2:3u}");
    runTest(PARSER, "TestAllTypes{single_int32: 1, single_int64: 2}");
    runTest(PARSER, "size(x) == x.size()");
    runTest(PARSER, "\"\\\"\"");
    runTest(PARSER, "[1,3,4][0]");
    runTest(PARSER, "x[\"a\"].single_int32 == 23");
    runTest(PARSER, "x.single_nested_message != null");
    runTest(PARSER, "false && !true || false ? 2 : 3");
    runTest(PARSER, "b\"abc\" + B\"def\"");
    runTest(PARSER, "1 + 2 * 3 - 1 / 2 == 6 % 1");
    runTest(PARSER, "---a");
    runTest(PARSER, "\"\\xC3\\XBF\"");
    runTest(PARSER, "\"\\303\\277\"");
    runTest(PARSER, "\"hi\\u263A \\u263Athere\"");
    runTest(PARSER, "\"\\U000003A8\\?\"");
    runTest(PARSER, "\"\\a\\b\\f\\n\\r\\t\\v'\\\"\\\\\\? Legal escapes\"");
    runTest(PARSER, "'😁' in ['😁', '😑', '😦']");
    runTest(
        PARSER,
        // Note, the ANTLR parse stack may recurse much more deeply and permit
        // more detailed expressions than the visitor can recurse over in
        // practice.
        "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[['just fine'],[1],[2],[3],[4],[5]]]]]]]"
            + "]]]]]]]]]]]]]]]]]]]]]]]]",
        false); // parse output not validated as it is too large.
    runTest(PARSER, "x.filter(y, y.filter(z, z > 0))");
    runTest(PARSER, "has(a.b).filter(c, c)");
    runTest(PARSER, "x.filter(y, y.exists(z, has(z.a)) && y.exists(z, has(z.b)))");
    runTest(PARSER, "noop_macro(123)");
    runTest(PARSER, "get_constant_macro()");
    runTest(PARSER, "a.?b[?0] && a[?c]");
    runTest(PARSER, "{?'key': value}");
    runTest(PARSER, "Msg{?field: value}");
    runTest(PARSER, "[?a, ?b]");
    runTest(PARSER, "[?a[?b]]");
    runTest(
        CelParserImpl.newBuilder()
            .setOptions(CelOptions.current().enableReservedIds(false).build())
            .build(),
        "while");
  }

  @Test
  public void parser_errors() {
    runTest(PARSER, "*@a | b");
    runTest(PARSER, "a | b");
    runTest(PARSER, "?");
    runTest(PARSER, "1 + $");
    runTest(PARSER, "1.all(2, 3)");
    runTest(PARSER, "1.exists(2, 3)");
    runTest(PARSER, "1 + +");
    runTest(PARSER, "\"\\xFh\"");
    runTest(PARSER, "\"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>\"");
    runTest(PARSER, "as");
    runTest(PARSER, "break");
    runTest(PARSER, "const");
    runTest(PARSER, "continue");
    runTest(PARSER, "else");
    runTest(PARSER, "for");
    runTest(PARSER, "function");
    runTest(PARSER, "if");
    runTest(PARSER, "import");
    runTest(PARSER, "in");
    runTest(PARSER, "let");
    runTest(PARSER, "loop");
    runTest(PARSER, "package");
    runTest(PARSER, "namespace");
    runTest(PARSER, "return");
    runTest(PARSER, "var");
    runTest(PARSER, "void");
    runTest(PARSER, "while");
    runTest(PARSER, "[1, 2, 3].map(var, var * var)");
    runTest(PARSER, "'😁' in ['😁', '😑', '😦']\n" + "   && in.😁");
    runTest(
        PARSER,
        "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[["
            + "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[["
            + "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[["
            + "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[['too many']]]]]]]]]]]]]]]]]]]]]]]]]]]]"
            + "]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]"
            + "]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]"
            + "]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]"
            + "]]]]]]");
    runTest(PARSER, "{\"a\": 1}.\"a\"");
    runTest(PARSER, "1 + 2\n3 +");
    runTest(PARSER, "TestAllTypes(){single_int32: 1, single_int64: 2}");
    runTest(PARSER, "{");
    runTest(PARSER, "t{>C}");
    runTest(PARSER, "has([(has((");

    CelParser parserWithoutOptionalSupport =
        CelParserImpl.newBuilder()
            .setOptions(CelOptions.current().enableOptionalSyntax(false).build())
            .build();
    runTest(parserWithoutOptionalSupport, "a.?b && a[?b]");
    runTest(parserWithoutOptionalSupport, "Msg{?field: value} && {?'key': value}");
    runTest(parserWithoutOptionalSupport, "[?a, ?b]");
  }

  @Test
  public void source_info() throws Exception {
    runSourceInfoTest("[{}, {'field': true}].exists(i, has(i.field))");
  }

  private void runTest(CelParser parser, String expression) {
    runTest(parser, expression, true);
  }

  private void runTest(CelParser parser, String expression, boolean validateParseOutput) {
    testOutput().println("I: " + expression);
    testOutput().println("=====>");

    CelSource source = CelSource.newBuilder(expression).setDescription("<input>").build();
    CelValidationResult parseResult = parser.parse(source);

    try {
      CelProtoAbstractSyntaxTree protoAst =
          CelProtoAbstractSyntaxTree.fromCelAst(parseResult.getAst());
      ParsedExpr parsedExpr = protoAst.toParsedExpr();
      if (validateParseOutput) {
        testOutput()
            .println(
                "P: "
                    + CelDebug.toAdornedDebugString(parsedExpr.getExpr(), new KindAndIdAdorner()));
        String locationOutput =
            CelDebug.toAdornedDebugString(
                parsedExpr.getExpr(), new LocationAdorner(parsedExpr.getSourceInfo()));
        if (!locationOutput.isEmpty()) {
          testOutput().println("L: " + locationOutput);
        }
      }

      String macroOutput = convertMacroCallsToString(parsedExpr.getSourceInfo());
      if (!macroOutput.isEmpty()) {
        testOutput().println("M: " + macroOutput);
      }
    } catch (CelValidationException e) {
      testOutput().println("E: " + e.getMessage());
    }

    testOutput().println();
  }

  private void runSourceInfoTest(String expression) throws Exception {
    CelAbstractSyntaxTree ast = PARSER.parse(expression).getAst();
    SourceInfo sourceInfo =
        CelProtoAbstractSyntaxTree.fromCelAst(ast).toParsedExpr().getSourceInfo();
    testOutput().println("I: " + expression);
    testOutput().println("=====>");
    testOutput().println("S: " + sourceInfo);
  }

  private String convertMacroCallsToString(SourceInfo sourceInfo) {
    KindAndIdAdorner macroCallsAdorner = new KindAndIdAdorner(sourceInfo);
    // Sort in ascending order so that nested macro calls are always in the same order for tests
    // output debug string. Ascending order keeps the macro calls map in order from outermost/first
    // macro to the innermost/last macro for readability.
    return sourceInfo.getMacroCallsMap().entrySet().stream()
        .sorted(reverseOrder(comparingByKey()))
        .map((entry) -> CelDebug.toAdornedDebugString(entry.getValue(), macroCallsAdorner))
        .collect(joining(",\n"));
  }

  private static final class KindAndIdAdorner implements CelAdorner {

    private final SourceInfo sourceInfo;

    KindAndIdAdorner() {
      this(SourceInfo.getDefaultInstance());
    }

    KindAndIdAdorner(SourceInfo sourceInfo) {
      this.sourceInfo = sourceInfo;
    }

    @Override
    public String adorn(ExprOrBuilder expr) {
      if (this.sourceInfo != null && this.sourceInfo.containsMacroCalls(expr.getId())) {
        return String.format(
            "^#%d:%s#",
            expr.getId(),
            this.sourceInfo.getMacroCallsOrThrow(expr.getId()).getCallExpr().getFunction());
      }

      if (expr.hasConstExpr()) {
        Constant constExpr = expr.getConstExpr();
        Descriptor descriptor = Constant.getDescriptor();
        OneofDescriptor oneof = findOneofByName(descriptor, "constant_kind");
        FieldDescriptor field = constExpr.getOneofFieldDescriptor(oneof);
        if (field.getType() == FieldDescriptor.Type.ENUM) {
          return String.format("^#%d:%s#", expr.getId(), getContainedName(field.getEnumType()));
        } else {
          return String.format(
              "^#%d:%s#", expr.getId(), Ascii.toLowerCase(field.getType().toString()));
        }
      }
      Descriptor descriptor = Expr.getDescriptor();
      OneofDescriptor oneof = findOneofByName(descriptor, "expr_kind");
      FieldDescriptor field = expr.getOneofFieldDescriptor(oneof);
      return String.format("^#%d:%s#", expr.getId(), getContainedName(field.getMessageType()));
    }

    @Override
    public String adorn(Expr.CreateStruct.EntryOrBuilder entry) {
      return String.format("^#%d:Expr.CreateStruct.Entry#", entry.getId());
    }
  }

  @AutoValue
  @Immutable
  abstract static class LineAndColumn {

    public abstract int getLine();

    public abstract int getColumn();
  }

  private static final class LocationAdorner implements CelAdorner {

    private final SourceInfo sourceInfo;

    LocationAdorner(SourceInfo sourceInfo) {
      this.sourceInfo = sourceInfo;
    }

    @Override
    public String adorn(ExprOrBuilder expr) {
      return getLocation(expr.getId())
          .map(
              location ->
                  String.format(
                      "^#%d[%d,%d]#", expr.getId(), location.getLine(), location.getColumn()))
          .orElseGet(() -> String.format("^#%d[NO_POS]#", expr.getId()));
    }

    @Override
    public String adorn(Expr.CreateStruct.EntryOrBuilder entry) {
      return getLocation(entry.getId())
          .map(
              location ->
                  String.format(
                      "^#%d[%d,%d]#", entry.getId(), location.getLine(), location.getColumn()))
          .orElseGet(() -> String.format("^#%d[NO_POS]#", entry.getId()));
    }

    private Optional<LineAndColumn> getLocation(long exprId) {
      Map<Long, Integer> positions = sourceInfo.getPositionsMap();
      Integer position = positions.get(exprId);
      if (position == null) {
        return Optional.empty();
      }
      int line = 1;
      for (int index = 0; index < sourceInfo.getLineOffsetsCount(); index++) {
        if (sourceInfo.getLineOffsets(index) > position) {
          break;
        } else {
          line++;
        }
      }
      int column = position;
      if (line > 1) {
        column = position - sourceInfo.getLineOffsets(line - 2);
      }
      return Optional.of(new AutoValue_CelParserParameterizedTest_LineAndColumn(line, column));
    }
  }

  private static OneofDescriptor findOneofByName(Descriptor descriptor, String name) {
    for (OneofDescriptor oneof : descriptor.getOneofs()) {
      if (oneof.getName().equals(name)) {
        return oneof;
      }
    }
    return null;
  }

  private static final Joiner JOINER = Joiner.on('.');

  private static String getContainedName(Descriptor descriptor) {
    Deque<String> parts = new ArrayDeque<>();
    parts.addFirst(descriptor.getName());
    Descriptor containing = descriptor.getContainingType();
    while (containing != null) {
      parts.addFirst(containing.getName());
      containing = containing.getContainingType();
    }
    return JOINER.join(parts);
  }

  private static String getContainedName(EnumDescriptor descriptor) {
    Deque<String> parts = new ArrayDeque<>();
    parts.addFirst(descriptor.getName());
    Descriptor containing = descriptor.getContainingType();
    while (containing != null) {
      parts.addFirst(containing.getName());
      containing = containing.getContainingType();
    }
    return JOINER.join(parts);
  }
}
