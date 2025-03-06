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

package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelMathExtensionsTest {
  private static final CelOptions CEL_OPTIONS =
      CelOptions.current().enableUnsignedLongs(false).build();
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .setOptions(CEL_OPTIONS)
          .addLibraries(CelExtensions.math(CEL_OPTIONS))
          .build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(CEL_OPTIONS)
          .addLibraries(CelExtensions.math(CEL_OPTIONS))
          .build();

  @Test
  @TestParameters("{expr: 'math.greatest(-5)', expectedResult: -5}")
  @TestParameters("{expr: 'math.greatest(5)', expectedResult: 5}")
  @TestParameters("{expr: 'math.greatest(1, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(1, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(1, 1u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(-3, 3)', expectedResult: 3}")
  @TestParameters("{expr: 'math.greatest(9, 10)', expectedResult: 10}")
  @TestParameters("{expr: 'math.greatest(15, 14)', expectedResult: 15}")
  @TestParameters(
      "{expr: 'math.greatest(1, 9223372036854775807)', expectedResult: 9223372036854775807}")
  @TestParameters(
      "{expr: 'math.greatest(9223372036854775807, 1)', expectedResult: 9223372036854775807}")
  @TestParameters("{expr: 'math.greatest(-9223372036854775808, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(1, -9223372036854775808)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(1, 1, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(3, 1, 10)', expectedResult: 10}")
  @TestParameters("{expr: 'math.greatest(1, 5, 2)', expectedResult: 5}")
  @TestParameters("{expr: 'math.greatest(-1, 1, 0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(dyn(1), 1, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(5, 1.0, 3u)', expectedResult: 5}")
  @TestParameters("{expr: 'math.greatest(5.4, 10, 3u, -5.0, 3.5)', expectedResult: 10}")
  @TestParameters(
      "{expr: 'math.greatest(5.4, 10, 3u, -5.0, 9223372036854775807)', expectedResult:"
          + " 9223372036854775807}")
  @TestParameters(
      "{expr: 'math.greatest(9223372036854775807, 10, 3u, -5.0, 0)', expectedResult:"
          + " 9223372036854775807}")
  @TestParameters("{expr: 'math.greatest([5.4, 10, 3u, -5.0, 3.5])', expectedResult: 10}")
  @TestParameters(
      "{expr: 'math.greatest([dyn(5.4), dyn(10), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " 10}")
  public void greatest_intResult_success(String expr, long expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expr: 'math.greatest(-5.0)', expectedResult: -5.0}")
  @TestParameters("{expr: 'math.greatest(5.0)', expectedResult: 5.0}")
  @TestParameters("{expr: 'math.greatest(1.0, 1.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.greatest(1.0, 1)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.greatest(1.0, 1u)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.greatest(-3, 3.0)', expectedResult: 3.0}")
  @TestParameters("{expr: 'math.greatest(9, 10.0)', expectedResult: 10.0}")
  @TestParameters("{expr: 'math.greatest(15.0, 14)', expectedResult: 15.0}")
  @TestParameters("{expr: 'math.greatest(14, 15.0)', expectedResult: 15.0}")
  @TestParameters("{expr: 'math.greatest(15.0, 14u)', expectedResult: 15.0}")
  @TestParameters("{expr: 'math.greatest(14u, 15.0)', expectedResult: 15.0}")
  @TestParameters("{expr: 'math.greatest(1, 1.797693e308)', expectedResult: 1.797693e308}")
  @TestParameters("{expr: 'math.greatest(1.797693e308, 1)', expectedResult: 1.797693e308}")
  @TestParameters("{expr: 'math.greatest(-1.797693e308, 1.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.greatest(1.0, -1.797693e308)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.greatest(1.0, 1.0, 1.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.greatest(3.0, 1.0, 10.0)', expectedResult: 10.0}")
  @TestParameters("{expr: 'math.greatest(1.0, 5.0, 2.0)', expectedResult: 5.0}")
  @TestParameters("{expr: 'math.greatest(-1.0, 1.0, 0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.greatest(dyn(1.0), 1.0, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(5.0, 1.0, 3u)', expectedResult: 5.0}")
  @TestParameters("{expr: 'math.greatest(5.4, 10.0, 3u, -5.0, 3.5)', expectedResult: 10}")
  @TestParameters(
      "{expr: 'math.greatest(5.4, 10, 3u, -5.0, 1.797693e308)', expectedResult:" + " 1.797693e308}")
  @TestParameters(
      "{expr: 'math.greatest(1.797693e308, 10, 3u, -5.0, 0)', expectedResult:" + " 1.797693e308}")
  @TestParameters("{expr: 'math.greatest([5.4, 10.0, 3u, -5.0, 3.5])', expectedResult: 10}")
  @TestParameters(
      "{expr: 'math.greatest([dyn(5.4), dyn(10.0), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " 10.0}")
  public void greatest_doubleResult_success(String expr, double expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expr: 'math.greatest(1.0, 1u)', expectedResult: '1.0'}")
  @TestParameters("{expr: 'math.greatest(15.0, 14u)', expectedResult: '15.0'}")
  @TestParameters("{expr: 'math.greatest(14u, 15.0)', expectedResult: '15.0'}")
  @TestParameters("{expr: 'math.greatest(1u, 1.797693e308)', expectedResult: '1.797693e308'}")
  @TestParameters("{expr: 'math.greatest(1.797693e308, 1u)', expectedResult: '1.797693e308'}")
  @TestParameters("{expr: 'math.greatest(-1.797693e308, 1.0)', expectedResult: '1.0'}")
  @TestParameters("{expr: 'math.greatest(5.0, 1.0, 3u)', expectedResult: '5.0'}")
  @TestParameters("{expr: 'math.greatest(5.0, 1.0, dyn(3u))', expectedResult: '5.0'}")
  @TestParameters("{expr: 'math.greatest(5.4, 10.0, 3u, -5.0, 3.5)', expectedResult: '10'}")
  @TestParameters(
      "{expr: 'math.greatest(5.4, 10, 3u, -5.0, 1.797693e308)', expectedResult: '1.797693e308'}")
  @TestParameters(
      "{expr: 'math.greatest(1.797693e308, 10, 3u, -5.0, 0)', expectedResult: '1.797693e308'}")
  @TestParameters("{expr: 'math.greatest([5.4, 10.0, 3u, -5.0, 3.5])', expectedResult: '10'}")
  @TestParameters(
      "{expr: 'math.greatest([dyn(5.4), dyn(10.0), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " '10.0'}")
  public void greatest_doubleResult_withUnsignedLongsEnabled_success(
      String expr, double expectedResult) throws Exception {
    CelOptions celOptions = CelOptions.current().enableUnsignedLongs(true).build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    double result = (double) celRuntime.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expr: 'math.greatest(5u)', expectedResult: 5}")
  @TestParameters("{expr: 'math.greatest(1u, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(1u, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(1u, 1u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(3u, 3.0)', expectedResult: 3}")
  @TestParameters("{expr: 'math.greatest(9u, 10u)', expectedResult: 10}")
  @TestParameters("{expr: 'math.greatest(15u, 14u)', expectedResult: 15}")
  @TestParameters(
      "{expr: 'math.greatest(1, 9223372036854775807u)', expectedResult: 9223372036854775807}")
  @TestParameters(
      "{expr: 'math.greatest(9223372036854775807u, 1)', expectedResult: 9223372036854775807}")
  @TestParameters("{expr: 'math.greatest(1u, 1, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(3u, 1u, 10u)', expectedResult: 10}")
  @TestParameters("{expr: 'math.greatest(1u, 5u, 2u)', expectedResult: 5}")
  @TestParameters("{expr: 'math.greatest(-1, 1u, 0u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(dyn(1u), 1, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.greatest(5u, 1.0, 3u)', expectedResult: 5}")
  @TestParameters("{expr: 'math.greatest(5.4, 10u, 3u, -5.0, 3.5)', expectedResult: 10}")
  @TestParameters(
      "{expr: 'math.greatest(5.4, 10, 3u, -5.0, 9223372036854775807)', expectedResult:"
          + " 9223372036854775807}")
  @TestParameters(
      "{expr: 'math.greatest(9223372036854775807, 10, 3u, -5.0, 0)', expectedResult:"
          + " 9223372036854775807}")
  @TestParameters("{expr: 'math.greatest([5.4, 10, 3u, -5.0, 3.5])', expectedResult: 10}")
  @TestParameters(
      "{expr: 'math.greatest([dyn(5.4), dyn(10), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " 10}")
  public void greatest_unsignedLongResult_withSignedLongType_success(
      String expr, long expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters(
      "{expr: 'math.greatest(18446744073709551615u)', expectedResult: '18446744073709551615'}")
  @TestParameters("{expr: 'math.greatest(1u, 1.0)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.greatest(1u, 1)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.greatest(1u, 1u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.greatest(3u, 3.0)', expectedResult: '3'}")
  @TestParameters("{expr: 'math.greatest(9u, 10u)', expectedResult: '10'}")
  @TestParameters("{expr: 'math.greatest(15u, 14u)', expectedResult: '15'}")
  @TestParameters(
      "{expr: 'math.greatest(1, 18446744073709551615u)', expectedResult: '18446744073709551615'}")
  @TestParameters(
      "{expr: 'math.greatest(18446744073709551615u, 1)', expectedResult: '18446744073709551615'}")
  @TestParameters("{expr: 'math.greatest(1u, 1, 1)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.greatest(3u, 1u, 10u)', expectedResult: '10'}")
  @TestParameters("{expr: 'math.greatest(1u, 5u, 2u)', expectedResult: '5'}")
  @TestParameters("{expr: 'math.greatest(-1, 1u, 0u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.greatest(dyn(1u), 1, 1.0)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.greatest(5u, 1.0, 3u)', expectedResult: '5'}")
  @TestParameters("{expr: 'math.greatest(5.4, 10u, 3u, -5.0, 3.5)', expectedResult: '10'}")
  @TestParameters(
      "{expr: 'math.greatest(5.4, 10, 3u, -5.0, 18446744073709551615u)', expectedResult:"
          + " '18446744073709551615'}")
  @TestParameters(
      "{expr: 'math.greatest(18446744073709551615u, 10, 3u, -5.0, 0)', expectedResult:"
          + " '18446744073709551615'}")
  @TestParameters("{expr: 'math.greatest([5.4, 10u, 3, -5.0, 3.5])', expectedResult: '10'}")
  @TestParameters(
      "{expr: 'math.greatest([dyn(5.4), dyn(10u), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " '10'}")
  public void greatest_unsignedLongResult_withUnsignedLongType_success(
      String expr, String expectedResult) throws Exception {
    CelOptions celOptions = CelOptions.current().enableUnsignedLongs(true).build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    UnsignedLong result = (UnsignedLong) celRuntime.createProgram(ast).eval();

    assertThat(result).isEqualTo(UnsignedLong.valueOf(expectedResult));
  }

  @Test
  public void greatest_noArgs_throwsCompilationException() {
    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> CEL_COMPILER.compile("math.greatest()").getAst());

    assertThat(e).hasMessageThat().contains("math.greatest() requires at least one argument");
  }

  @Test
  @TestParameters("{expr: 'math.greatest(''test'')'}")
  @TestParameters("{expr: 'math.greatest({})'}")
  @TestParameters("{expr: 'math.greatest([])'}")
  public void greatest_invalidSingleArg_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e).hasMessageThat().contains("math.greatest() invalid single argument value");
  }

  @Test
  @TestParameters("{expr: 'math.greatest(1, true)'}")
  @TestParameters("{expr: 'math.greatest(1, 2, ''test'')'}")
  @TestParameters("{expr: 'math.greatest([1, false])'}")
  @TestParameters("{expr: 'math.greatest([1, 2, true])'}")
  @TestParameters("{expr: 'math.greatest([1, {}, 2])'}")
  @TestParameters("{expr: 'math.greatest([1, [], 2])'}")
  public void greatest_invalidArgs_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e)
        .hasMessageThat()
        .contains("math.greatest() simple literal arguments must be numeric");
  }

  @Test
  @TestParameters("{expr: 'math.greatest(1, 2, dyn(''test''))'}")
  @TestParameters("{expr: 'math.greatest([1, dyn(false)])'}")
  @TestParameters("{expr: 'math.greatest([1, 2, dyn(true)])'}")
  @TestParameters("{expr: 'math.greatest([1, dyn({}), 2])'}")
  @TestParameters("{expr: 'math.greatest([1, dyn([]), 2])'}")
  public void greatest_invalidDynArgs_throwsRuntimeException(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> CEL_RUNTIME.createProgram(ast).eval());

    assertThat(e).hasMessageThat().contains("Function 'math_@max_list_dyn' failed with arg(s)");
  }

  @Test
  public void greatest_listVariableIsEmpty_throwsRuntimeException() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.math(CEL_OPTIONS))
            .addVar("listVar", ListType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("math.greatest(listVar)").getAst();

    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class,
            () ->
                CEL_RUNTIME
                    .createProgram(ast)
                    .eval(ImmutableMap.of("listVar", ImmutableList.of())));

    assertThat(e).hasMessageThat().contains("Function 'math_@max_list_dyn' failed with arg(s)");
    assertThat(e)
        .hasCauseThat()
        .hasMessageThat()
        .contains("math.@max(list) argument must not be empty");
  }

  @Test
  @TestParameters("{expr: '100.greatest(1) == 1'}")
  @TestParameters("{expr: 'dyn(100).greatest(1) == 1'}")
  public void greatest_nonProtoNamespace_success(String expr) throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.math(CEL_OPTIONS))
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "greatest",
                    CelOverloadDecl.newMemberOverload(
                        "int_greatest_int", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "int_greatest_int", Long.class, Long.class, (arg1, arg2) -> arg2))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expr: 'math.least(-5)', expectedResult: -5}")
  @TestParameters("{expr: 'math.least(5)', expectedResult: 5}")
  @TestParameters("{expr: 'math.least(1, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1, 1u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(-3, 3)', expectedResult: -3}")
  @TestParameters("{expr: 'math.least(3, -3)', expectedResult: -3}")
  @TestParameters("{expr: 'math.least(9, 10)', expectedResult: 9}")
  @TestParameters("{expr: 'math.least(15, 14)', expectedResult: 14}")
  @TestParameters(
      "{expr: 'math.least(1, -9223372036854775808)', expectedResult: -9223372036854775808}")
  @TestParameters(
      "{expr: 'math.least(-9223372036854775808, 1)', expectedResult: -9223372036854775808}")
  @TestParameters("{expr: 'math.least(9223372036854775807, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1, 9223372036854775807)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1, 1, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(3, -1, 10)', expectedResult: -1}")
  @TestParameters("{expr: 'math.least(1, 5, 2)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(-1, 1, 0)', expectedResult: -1}")
  @TestParameters("{expr: 'math.least(dyn(1), 1, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(-5, 1.0, 3u)', expectedResult: -5}")
  @TestParameters("{expr: 'math.least(5.4, -10, 3u, -5.0, 3.5)', expectedResult: -10}")
  @TestParameters(
      "{expr: 'math.least(5.4, 10, 3u, -5.0, -9223372036854775808)', expectedResult:"
          + " -9223372036854775808}")
  @TestParameters(
      "{expr: 'math.least(-9223372036854775808, 10, 3u, -5.0, 0)', expectedResult:"
          + " -9223372036854775808}")
  @TestParameters("{expr: 'math.least([5.4, -10, 3u, -5.0, 3.5])', expectedResult: -10}")
  @TestParameters(
      "{expr: 'math.least([dyn(5.4), dyn(-10), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " -10}")
  public void least_intResult_success(String expr, long expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expr: 'math.least(-5.0)', expectedResult: -5.0}")
  @TestParameters("{expr: 'math.least(5.0)', expectedResult: 5.0}")
  @TestParameters("{expr: 'math.least(1.0, 1.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(1.0, 1)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(1.0, 1u)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(3, -3.0)', expectedResult: -3.0}")
  @TestParameters("{expr: 'math.least(9, -10.0)', expectedResult: -10.0}")
  @TestParameters("{expr: 'math.least(15, 14.0)', expectedResult: 14.0}")
  @TestParameters("{expr: 'math.least(15, 14.0)', expectedResult: 14.0}")
  @TestParameters("{expr: 'math.least(13.0, 14u)', expectedResult: 13.0}")
  @TestParameters("{expr: 'math.least(14u, 13.0)', expectedResult: 13.0}")
  @TestParameters("{expr: 'math.least(1, -1.797693e308)', expectedResult: -1.797693e308}")
  @TestParameters("{expr: 'math.least(-1.797693e308, 1)', expectedResult: -1.797693e308}")
  @TestParameters("{expr: 'math.least(1.797693e308, 1.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(1.0, 1.797693e308)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(1.0, 1.0, 1.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(3.0, 1.0, 10.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(1.0, 5.0, 2.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(-1.0, 1.0, 0)', expectedResult: -1.0}")
  @TestParameters("{expr: 'math.least(dyn(1.0), 1.0, 1.0)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(5.0, 1.0, 3u)', expectedResult: 1.0}")
  @TestParameters("{expr: 'math.least(5.4, 10.0, 3u, -5.0, 3.5)', expectedResult: -5.0}")
  @TestParameters(
      "{expr: 'math.least(5.4, 10, 3u, -5.0, -1.797693e308)', expectedResult: -1.797693e308}")
  @TestParameters(
      "{expr: 'math.least(-1.797693e308, 10, 3u, -5.0, 0)', expectedResult: -1.797693e308}")
  @TestParameters("{expr: 'math.least([5.4, 10.0, 3u, -5.0, 3.5])', expectedResult: -5.0}")
  @TestParameters(
      "{expr: 'math.least([dyn(5.4), dyn(10.0), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " -5.0}")
  public void least_doubleResult_success(String expr, double expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expr: 'math.least(1.0, 1u)', expectedResult: '1.0'}")
  @TestParameters("{expr: 'math.least(13.0, 14u)', expectedResult: '13.0'}")
  @TestParameters("{expr: 'math.least(14u, 13.0)', expectedResult: '13.0'}")
  @TestParameters("{expr: 'math.least(1u, -1.797693e308)', expectedResult: '-1.797693e308'}")
  @TestParameters("{expr: 'math.least(-1.797693e308, 1u)', expectedResult: '-1.797693e308'}")
  @TestParameters("{expr: 'math.least(1.797693e308, 1.0)', expectedResult: '1.0'}")
  @TestParameters("{expr: 'math.least(5.0, 1.0, 3u)', expectedResult: '1.0'}")
  @TestParameters("{expr: 'math.least(5.0, 1.0, dyn(3u))', expectedResult: '1.0'}")
  @TestParameters("{expr: 'math.least(5.4, 10.0, 3u, -5.0, 3.5)', expectedResult: '-5.0'}")
  @TestParameters(
      "{expr: 'math.least(5.4, 10, 3u, -5.0, -1.797693e308)', expectedResult: '-1.797693e308'}")
  @TestParameters(
      "{expr: 'math.least(-1.797693e308, 10, 3u, -5.0, 0)', expectedResult: '-1.797693e308'}")
  @TestParameters("{expr: 'math.least([5.4, 10.0, 3u, -5.0, 3.5])', expectedResult: '-5.0'}")
  @TestParameters(
      "{expr: 'math.least([dyn(5.4), dyn(10.0), dyn(3u), dyn(-5.0), dyn(3.5)])', expectedResult:"
          + " '-5.0'}")
  public void least_doubleResult_withUnsignedLongsEnabled_success(
      String expr, double expectedResult) throws Exception {
    CelOptions celOptions = CelOptions.current().enableUnsignedLongs(true).build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    double result = (double) celRuntime.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expr: 'math.least(5u)', expectedResult: 5}")
  @TestParameters("{expr: 'math.least(1u, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1u, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1u, 1u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(3u, 3.0)', expectedResult: 3}")
  @TestParameters("{expr: 'math.least(9u, 10u)', expectedResult: 9}")
  @TestParameters("{expr: 'math.least(15u, 14u)', expectedResult: 14}")
  @TestParameters("{expr: 'math.least(1, 9223372036854775807u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(9223372036854775807u, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1u, 1, 1)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(3u, 1u, 10u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(1u, 5u, 2u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(9, 1u, 0u)', expectedResult: 0}")
  @TestParameters("{expr: 'math.least(dyn(1u), 1, 1.0)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(5.0, 1u, 3u)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(5.4, 1u, 3u, 9, 3.5)', expectedResult: 1}")
  @TestParameters("{expr: 'math.least(5.4, 10, 3u, 5.0, 9223372036854775807)', expectedResult: 3}")
  @TestParameters("{expr: 'math.least(9223372036854775807, 10, 3u, 5.0, 0)', expectedResult: 0}")
  @TestParameters("{expr: 'math.least([5.4, 10, 3u, 5.0, 3.5])', expectedResult: 3}")
  @TestParameters(
      "{expr: 'math.least([dyn(5.4), dyn(10), dyn(3u), dyn(5.0), dyn(3.5)])', expectedResult: 3}")
  public void least_unsignedLongResult_withSignedLongType_success(String expr, long expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters(
      "{expr: 'math.least(18446744073709551615u)', expectedResult: '18446744073709551615'}")
  @TestParameters("{expr: 'math.least(1u, 1.0)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(1u, 1u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(1u, 1u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(3u, 3.0)', expectedResult: '3'}")
  @TestParameters("{expr: 'math.least(9u, 10u)', expectedResult: '9'}")
  @TestParameters("{expr: 'math.least(15u, 14u)', expectedResult: '14'}")
  @TestParameters("{expr: 'math.least(15, 14u)', expectedResult: '14'}")
  @TestParameters("{expr: 'math.least(14u, 15)', expectedResult: '14'}")
  @TestParameters("{expr: 'math.least(1u, 18446744073709551615u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(18446744073709551615u, 1u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(1u, 1, 1)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(3u, 1u, 10u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(1u, 5u, 2u)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(4, 1u, 0u)', expectedResult: '0'}")
  @TestParameters("{expr: 'math.least(dyn(1u), 1u, 1.0)', expectedResult: '1'}")
  @TestParameters("{expr: 'math.least(5u, 1.0, 0u)', expectedResult: '0'}")
  @TestParameters("{expr: 'math.least(5.4, 10u, 3u, 5.0, 3.5)', expectedResult: '3'}")
  @TestParameters(
      "{expr: 'math.least(5.4, 10, 3u, 0u, 18446744073709551615u)', expectedResult: '0'}")
  @TestParameters(
      "{expr: 'math.least(18446744073709551615u, 10, 3u, 5.0, 0u)', expectedResult: '0'}")
  @TestParameters("{expr: 'math.least([5.4, 10u, 3u, 5.0, 3.5])', expectedResult: '3'}")
  @TestParameters(
      "{expr: 'math.least([dyn(5.4), dyn(10u), dyn(3u), dyn(5.0), dyn(3.5)])', expectedResult:"
          + " '3'}")
  public void least_unsignedLongResult_withUnsignedLongType_success(
      String expr, String expectedResult) throws Exception {
    CelOptions celOptions = CelOptions.current().build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setOptions(celOptions)
            .addLibraries(CelExtensions.math(celOptions))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    UnsignedLong result = (UnsignedLong) celRuntime.createProgram(ast).eval();

    assertThat(result).isEqualTo(UnsignedLong.valueOf(expectedResult));
  }

  @Test
  public void least_noArgs_throwsCompilationException() {
    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> CEL_COMPILER.compile("math.least()").getAst());

    assertThat(e).hasMessageThat().contains("math.least() requires at least one argument");
  }

  @Test
  @TestParameters("{expr: 'math.least(''test'')'}")
  @TestParameters("{expr: 'math.least({})'}")
  @TestParameters("{expr: 'math.least([])'}")
  public void least_invalidSingleArg_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e).hasMessageThat().contains("math.least() invalid single argument value");
  }

  @Test
  @TestParameters("{expr: 'math.least(1, true)'}")
  @TestParameters("{expr: 'math.least(1, 2, ''test'')'}")
  @TestParameters("{expr: 'math.least([1, false])'}")
  @TestParameters("{expr: 'math.least([1, 2, true])'}")
  @TestParameters("{expr: 'math.least([1, {}, 2])'}")
  @TestParameters("{expr: 'math.least([1, [], 2])'}")
  public void least_invalidArgs_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e)
        .hasMessageThat()
        .contains("math.least() simple literal arguments must be numeric");
  }

  @Test
  @TestParameters("{expr: 'math.least(1, 2, dyn(''test''))'}")
  @TestParameters("{expr: 'math.least([1, dyn(false)])'}")
  @TestParameters("{expr: 'math.least([1, 2, dyn(true)])'}")
  @TestParameters("{expr: 'math.least([1, dyn({}), 2])'}")
  @TestParameters("{expr: 'math.least([1, dyn([]), 2])'}")
  public void least_invalidDynArgs_throwsRuntimeException(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> CEL_RUNTIME.createProgram(ast).eval());

    assertThat(e).hasMessageThat().contains("Function 'math_@min_list_dyn' failed with arg(s)");
  }

  @Test
  public void least_listVariableIsEmpty_throwsRuntimeException() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.math(CEL_OPTIONS))
            .addVar("listVar", ListType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("math.least(listVar)").getAst();

    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class,
            () ->
                CEL_RUNTIME
                    .createProgram(ast)
                    .eval(ImmutableMap.of("listVar", ImmutableList.of())));

    assertThat(e).hasMessageThat().contains("Function 'math_@min_list_dyn' failed with arg(s)");
    assertThat(e)
        .hasCauseThat()
        .hasMessageThat()
        .contains("math.@min(list) argument must not be empty");
  }

  @Test
  @TestParameters("{expr: '100.least(1) == 1'}")
  @TestParameters("{expr: 'dyn(100).least(1) == 1'}")
  public void least_nonProtoNamespace_success(String expr) throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.math(CEL_OPTIONS))
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "least",
                    CelOverloadDecl.newMemberOverload(
                        "int_least", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from("int_least", Long.class, Long.class, (arg1, arg2) -> arg2))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
  }
}
