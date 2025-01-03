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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelMap;
import dev.cel.common.ast.CelExpr.CelStruct;
import dev.cel.extensions.CelOptionalLibrary;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelUnparserImplTest {

  private final CelParser parser =
      CelParserImpl.newBuilder()
          .setOptions(
              CelOptions.newBuilder()
                  .enableQuotedIdentifierSyntax(true)
                  .populateMacroCalls(true)
                  .build())
          .addLibraries(CelOptionalLibrary.INSTANCE)
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .build();

  private final CelUnparserImpl unparser = new CelUnparserImpl();

  private static final class ValidExprDataProvider extends TestParameterValuesProvider {
    @Override
    public List<String> provideValues(Context context) {
      return Arrays.asList(
          "a + b - c",
          "a && b && c && d && e",
          "a || b && (c || d) && e",
          "a ? b : c",
          "a[1][\"b\"]",
          "x[\"a\"].single_int32 == 23",
          "a * (b / c) % 0",
          "a + b * c",
          "(a + b) * c / (d - e)",
          "a * b / c % 0",
          "!true",
          "-num",
          "a || b || c || d || e",
          "-(1 * 2)",
          "-(1 + 2)",
          "(x > 5) ? (x - 5) : 0",
          "size(a ? (b ? c : d) : e)",
          "a.hello(\"world\")",
          "zero()",
          "one(\"a\")",
          "and(d, 32u)",
          "max(a, b, 100)",
          "x != \"a\"",
          "[]",
          "[1]",
          "[\"hello, world\", \"goodbye, world\", \"sure, why not?\"]",
          "-42.101",
          "false",
          "-405069",
          "null",
          "\"hello:\t'world'\"",
          "true",
          "42u",
          "my_ident",
          "has(hello.world)",
          "{}",
          "{\"a\": a.b.c, b\"\\142\": bytes(a.b.c)}",
          "{a: a, b: a.b, c: a.b.c, a ? b : c: false, a || b: true}",
          "v1alpha1.Expr{}",
          "v1alpha1.Expr{id: 1, call_expr: v1alpha1.Call_Expr{function: \"name\"}}",
          "a.b.c",
          "a[b][c].name",
          "(a + b).name",
          "(a ? b : c).name",
          "(a ? b : c)[0]",
          "(a1 && a2) ? b : c",
          "a ? (b1 || b2) : (c1 && c2)",
          "(a ? b : c).method(d)",
          "a + b + c + d",
          "foo.`a.b`",
          "foo.`a/b`",
          "foo.`a-b`",
          "foo.`a b`",
          "foo.`in`",
          "Foo{`a.b`: foo}",
          "Foo{`a/b`: foo}",
          "Foo{`a-b`: foo}",
          "Foo{`a b`: foo}",

          // Constants
          "true",
          "4",
          "4u",

          // Sequences
          "[1, 2u]",
          "{1: 2u, 2: 3u}",

          // Messages
          "TestAllTypes{single_int32: 1, single_int64: 2}",

          // Conditionals
          "false && !true || false",
          "false && (!true || false)",
          "(false && !true || false) ? 2 : 3",
          "(x < 5) ? x : 5",
          "(x > 5) ? (x - 5) : 0",
          "(x > 5) ? ((x > 10) ? (x - 10) : 5) : 0",
          "a in b",

          // Calculations
          "(1 + 2) * 3",
          "1 + 2 * 3",
          "-(1 * 2)",

          // Comprehensions
          "[1, 2, 3].all(x, x > 0)",
          "[1, 2, 3].exists(x, x > 0)",
          "[1, 2, 3].map(x, x >= 2, x * 4)",
          "[1, 2, 3].exists_one(x, x >= 2)",
          "[[1], [2], [3]].all(x, x.all(y, y >= 2))",
          "(has(x.y) ? x.y : []).filter(z, z == \"zed\")",
          "[[1], [2], [3]].map(x, x.filter(y, y > 1))",
          "[1, 2, 3].map(x, x >= 2, x * 4).filter(x, x <= 10)",
          "{a: has(b.c)}.exists(k, k != \"\")",
          "{a: [1, 2].all(i > 0)}.exists(k, k != \"\")",

          // Macros
          "has(x[\"a\"].single_int32)",
          "has(x.`foo-bar`.single_int32)",

          // This is a filter expression but is decompiled back to
          // map(x, filter_function, x) for which the evaluation is
          // equal to filter(x, filter_function).
          "[1, 2, 3].map(x, x >= 2, x)",

          // Index
          "x[\"a\"].single_int32 == 23",
          "a[1][\"b\"]",

          // Functions
          "x != \"a\"",
          "size(x) == x.size()",

          // Long string
          "Loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong",

          // Optionals
          "a.?b",
          "a[?b]",
          "[?a, ?b, c]",
          "{?a: b, c: d}",
          "v1alpha1.Expr{?id: id, call_expr: v1alpha1.Call_Expr{function: \"name\"}}");
    }
  }

  @Test
  public void unparse_succeeds(
      @TestParameter(valuesProvider = ValidExprDataProvider.class) String originalExpr)
      throws Exception {
    CelAbstractSyntaxTree astOne = parser.parse(originalExpr, "unparser").getAst();

    String unparsedResult = unparser.unparse(astOne);

    assertThat(originalExpr).isEqualTo(unparsedResult);
    // parse again, confirm it's the same result
    CelAbstractSyntaxTree astTwo = parser.parse(unparsedResult, "unparser").getAst();

    assertThat(CelProtoAbstractSyntaxTree.fromCelAst(astTwo).toParsedExpr())
        .isEqualTo(CelProtoAbstractSyntaxTree.fromCelAst(astOne).toParsedExpr());
  }

  private static final class InvalidExprDataProvider extends TestParameterValuesProvider {
    @Override
    public List<CelExpr> provideValues(Context context) {
      return Arrays.asList(
          CelExpr.newBuilder().build(), // empty expr
          CelExpr.newBuilder()
              .setCall(
                  CelCall.newBuilder()
                      .setFunction("_&&_")
                      .addArgs(CelExpr.newBuilder().build())
                      .addArgs(CelExpr.newBuilder().build())
                      .build())
              .build(), // bad args
          CelExpr.newBuilder()
              .setStruct(
                  CelStruct.newBuilder()
                      .setMessageName("Msg")
                      .addEntries(
                          CelStruct.Entry.newBuilder()
                              .setId(0)
                              .setValue(CelExpr.newBuilder().build())
                              .setFieldKey("field")
                              .build())
                      .build())
              .build(), // bad struct
          CelExpr.newBuilder()
              .setMap(
                  CelMap.newBuilder()
                      .addEntries(
                          CelMap.Entry.newBuilder()
                              .setId(0)
                              .setValue(CelExpr.newBuilder().build())
                              .setKey(CelExpr.newBuilder().build())
                              .build())
                      .build())
              .build(), // bad map
          CelExpr.newBuilder()
              .setCall(
                  CelCall.newBuilder()
                      .setFunction("_[_]")
                      .addArgs(CelExpr.newBuilder().build())
                      .build())
              .build() // bad index
          );
    }
  }

  @Test
  public void unparse_fails(
      @TestParameter(valuesProvider = InvalidExprDataProvider.class) CelExpr invalidExpr) {
    Throwable thrown =
        assertThrows(
            Throwable.class,
            () ->
                unparser.unparse(
                    CelAbstractSyntaxTree.newParsedAst(
                        invalidExpr, CelSource.newBuilder().build())));

    assertThat(thrown).hasMessageThat().contains("unexpected");
  }

  @Test
  public void unparse_comprehensionWithoutMacroCallTracking_presenceTestSucceeds()
      throws Exception {
    CelParser parser =
        CelParserImpl.newBuilder()
            .setOptions(CelOptions.newBuilder().populateMacroCalls(false).build())
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .build();
    CelAbstractSyntaxTree ast = parser.parse("has(hello.world)").getAst();

    assertThat(unparser.unparse(ast)).isEqualTo("has(hello.world)");
  }

  @Test
  public void unparse_comprehensionWithoutMacroCallTracking_throwsException() throws Exception {
    CelParser parser =
        CelParserImpl.newBuilder()
            .setOptions(CelOptions.newBuilder().populateMacroCalls(false).build())
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .build();
    CelAbstractSyntaxTree ast = parser.parse("[1, 2, 3].all(x, x > 0)").getAst();

    UnsupportedOperationException e =
        assertThrows(UnsupportedOperationException.class, () -> unparser.unparse(ast));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Comprehension unparsing requires macro calls to be populated. Ensure the option is"
                + " enabled.");
  }

  @Test
  public void unparse_macroWithReceiverStyleArg() throws Exception {
    CelParser parser =
        CelParserImpl.newBuilder()
            .setOptions(CelOptions.newBuilder().populateMacroCalls(true).build())
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .build();
    CelAbstractSyntaxTree ast =
        parser.parse("[\"a\"].all(x, x.trim().lowerAscii().contains(\"b\"))").getAst();

    assertThat(unparser.unparse(ast))
        .isEqualTo("[\"a\"].all(x, x.trim().lowerAscii().contains(\"b\"))");
  }
}
