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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.Expr.CreateStruct;
import dev.cel.expr.Expr.CreateStruct.Entry;
import dev.cel.expr.ParsedExpr;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelUnparserImplTest {

  private final CelParserImpl parser =
      CelParserImpl.newBuilder()
          .setOptions(CelOptions.newBuilder().populateMacroCalls(true).build())
          .addMacros(CelMacro.STANDARD_MACROS)
          .build();

  private final CelUnparserImpl unparser = new CelUnparserImpl();

  private static final class ValidExprDataProvider implements TestParameterValuesProvider {
    @Override
    public List<String> provideValues() {
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
          "Loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong");
    }
  }

  @Test
  public void unparse_succeeds(
      @TestParameter(valuesProvider = ValidExprDataProvider.class) String originalExpr)
      throws Exception {
    ParsedExpr parsedExprOne =
        CelProtoAbstractSyntaxTree.fromCelAst(parser.parse(originalExpr, "unparser").getAst())
            .toParsedExpr();

    String unparsedResult = unparser.unparse(parsedExprOne);

    assertThat(originalExpr).isEqualTo(unparsedResult);
    // parse again, confirm it's the same result
    ParsedExpr parsedExprTwo =
        CelProtoAbstractSyntaxTree.fromCelAst(parser.parse(unparsedResult, "unparser").getAst())
            .toParsedExpr();
    assertThat(parsedExprTwo).isEqualTo(parsedExprOne);
  }

  private static final class InvalidExprDataProvider implements TestParameterValuesProvider {
    @Override
    public List<Expr> provideValues() {
      return Arrays.asList(
          Expr.getDefaultInstance(), // empty expr
          Expr.newBuilder().setConstExpr(Constant.getDefaultInstance()).build(), // bad_constant
          Expr.newBuilder()
              .setCallExpr(
                  Call.newBuilder()
                      .setFunction("_&&_")
                      .addArgs(Expr.getDefaultInstance())
                      .addArgs(Expr.getDefaultInstance())
                      .build())
              .build(), // bad args
          Expr.newBuilder()
              .setStructExpr(
                  CreateStruct.newBuilder()
                      .setMessageName("Msg")
                      .addEntries(Entry.newBuilder().setFieldKey("field").build())
                      .build())
              .build(), // bad struct
          Expr.newBuilder()
              .setStructExpr(
                  CreateStruct.newBuilder()
                      .setMessageName("Msg")
                      .addEntries(Entry.newBuilder().setMapKey(Expr.getDefaultInstance()).build())
                      .build())
              .build(), // bad map
          Expr.newBuilder()
              .setCallExpr(
                  Call.newBuilder().setFunction("_[_]").addArgs(Expr.getDefaultInstance()).build())
              .build() // bad index
          );
    }
  }

  @Test
  public void unparse_fails(
      @TestParameter(valuesProvider = InvalidExprDataProvider.class) Expr invalidExpr) {
    Throwable thrown =
        assertThrows(
            Throwable.class,
            () -> unparser.unparse(ParsedExpr.newBuilder().setExpr(invalidExpr).build()));

    assertThat(thrown).hasMessageThat().contains("unexpected");
  }
}
