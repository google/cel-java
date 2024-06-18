// Copyright 2024 Google LLC
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
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelSetsExtensions.Function;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelSetsExtensionsTest {
  private static final CelCompiler COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addLibraries(CelExtensions.sets())
          .addVar("list", ListType.create(SimpleType.INT))
          .addVar("subList", ListType.create(SimpleType.INT))
          .build();

  private static final CelRuntime RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(CelExtensions.sets()).build();

  @Test
  public void contains_integerListWithSameValue_succeeds() throws Exception {
    ImmutableList<Integer> list = ImmutableList.of(1, 2, 3, 4);
    ImmutableList<Integer> subList = ImmutableList.of(1, 2, 3, 4);
    CelAbstractSyntaxTree ast = COMPILER.compile("sets.contains(list, subList)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval(ImmutableMap.of("list", list, "subList", subList));

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void contains_integerListAsExpression_succeeds() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("sets.contains([1, 1], [1])").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  @TestParameters("{list: [1, 2, 3, 4], subList: [1, 2, 3, 4], expected: true}")
  @TestParameters("{list: [5, 4, 3, 2, 1], subList: [1, 2, 3], expected: true}")
  @TestParameters("{list: [5, 4, 3, 2, 1], subList: [1, 1, 1, 1, 1], expected: true}")
  @TestParameters("{list: [], subList: [], expected: true}")
  @TestParameters("{list: [1], subList: [], expected: true}")
  @TestParameters("{list: [], subList: [1], expected: false}")
  @TestParameters("{list: [1], subList: [1], expected: true}")
  @TestParameters("{list: [1], subList: [1, 1], expected: true}")
  @TestParameters("{list: [1, 1], subList: [1, 1], expected: true}")
  @TestParameters("{list: [2, 1], subList: [1], expected: true}")
  @TestParameters("{list: [1, 2, 3, 4], subList: [2, 3], expected: true}")
  @TestParameters("{list: [1], subList: [2], expected: false}")
  @TestParameters("{list: [1], subList: [1, 2], expected: false}")
  public void contains_withIntTypes_succeeds(
      List<Integer> list, List<Integer> subList, boolean expected) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("sets.contains(list, subList)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval(ImmutableMap.of("list", list, "subList", subList));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @TestParameters("{list: [1.0], subList: [1.0, 1.0], expected: true}")
  @TestParameters("{list: [1.0, 1.00], subList: [1], expected: true}")
  @TestParameters("{list: [1.0], subList: [1.00], expected: true}")
  @TestParameters("{list: [1.414], subList: [], expected: true}")
  @TestParameters("{list: [], subList: [1.414], expected: false}")
  @TestParameters("{list: [3.14, 2.71], subList: [2.71], expected: true}")
  @TestParameters("{list: [3.9], subList: [3.1], expected: false}")
  @TestParameters("{list: [3.2], subList: [3.1], expected: false}")
  @TestParameters("{list: [2, 3.0], subList: [2, 3], expected: true}")
  public void contains_withDoubleTypes_succeeds(
      List<Double> list, List<Double> subList, boolean expected) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("sets.contains(list, subList)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval(ImmutableMap.of("list", list, "subList", subList));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @TestParameters("{expression: 'sets.contains([[1], [2, 3]], [[2, 3]])', expected: true}")
  @TestParameters("{expression: 'sets.contains([[1], [2], [3]], [[2, 3]])', expected: false}")
  @TestParameters(
      "{expression: 'sets.contains([[1, 2], [2, 3]], [[1], [2, 3.0]])', expected: false}")
  @TestParameters("{expression: 'sets.contains([[1], [2, 3.0]], [[2, 3]])', expected: true}")
  public void contains_withNestedLists_succeeds(String expression, boolean expected)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @TestParameters("{expression: 'sets.contains([1, \"1\"], [1])', expected: true}")
  @TestParameters("{expression: 'sets.contains([1], [1, \"1\"])', expected: false}")
  public void contains_withMixingIntAndString_succeeds(String expression, boolean expected)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @TestParameters("{expression: 'sets.contains([1], [\"1\"])'}")
  @TestParameters("{expression: 'sets.contains([\"1\"], [1])'}")
  public void contains_withMixingIntAndString_throwsException(String expression) throws Exception {
    CelValidationResult invalidData = COMPILER.compile(expression);

    assertThat(invalidData.getErrors()).hasSize(1);
    assertThat(invalidData.getErrors().get(0).getMessage())
        .contains("found no matching overload for 'sets.contains'");
  }

  @Test
  public void contains_withMixedValues_succeeds() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("sets.contains([1, 2], [2u, 2.0])").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  @TestParameters("{expression: 'sets.contains([[1], [2, 3.0]], [[2, 3]])', expected: true}")
  @TestParameters(
      "{expression: 'sets.contains([[1], [2, 3.0], [[[[[5]]]]]], [[[[[[5]]]]]])', expected: true}")
  @TestParameters(
      "{expression: 'sets.contains([[1], [2, 3.0], [[[[[5]]]]]], [[[[[[5, 1]]]]]])', expected:"
          + " false}")
  @TestParameters(
      "{expression: 'sets.contains([[1], [2, 3.0], [[[[[5, 1]]]]]], [[[[[[5]]]]]])', expected:"
          + " false}")
  @TestParameters(
      "{expression: 'sets.contains([[[[[[5]]]]]], [[1], [2, 3.0], [[[[[5]]]]]])', expected: false}")
  public void contains_withMultiLevelNestedList_succeeds(String expression, boolean expected)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @TestParameters("{expression: 'sets.equivalent([], [])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([1], [1])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([1], [1, 1])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([1, 1], [1])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([[1], [2, 3]], [[1], [2, 3]])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([2, 1], [1])', expected: false}")
  @TestParameters("{expression: 'sets.equivalent([1], [1, 2])', expected: false}")
  @TestParameters("{expression: 'sets.equivalent([1, 2], [1, 2, 3])', expected: false}")
  @TestParameters("{expression: 'sets.equivalent([1, 2], [2, 2, 2])', expected: false}")
  public void equivalent_withIntTypes_succeeds(String expression, boolean expected)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @TestParameters("{expression: 'sets.equivalent([1, 2, 3], [3u, 2.0, 1])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([1], [1u, 1.0])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([1], [1u, 1.0])', expected: true}")
  @TestParameters(
      "{expression: 'sets.equivalent([[1.0], [2, 3]], [[1], [2, 3.0]])', expected: true}")
  @TestParameters("{expression: 'sets.equivalent([1, 2], [2u, 2, 2.0])', expected: false}")
  @TestParameters("{expression: 'sets.equivalent([1, 2], [1u, 2, 2.3])', expected: false}")
  public void equivalent_withMixedTypes_succeeds(String expression, boolean expected)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @TestParameters("{expression: 'sets.intersects([], [])', expected: false}")
  @TestParameters("{expression: 'sets.intersects([1], [])', expected: false}")
  @TestParameters("{expression: 'sets.intersects([], [1])', expected: false}")
  @TestParameters("{expression: 'sets.intersects([1], [1])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1], [2])', expected: false}")
  @TestParameters("{expression: 'sets.intersects([1], [1, 1])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1, 1], [1])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([2, 1], [1])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1], [1, 2])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1], [1.0, 2])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1, 2], [2u, 2, 2.0])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1, 2], [1, 2, 2.3])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([0, 1, 2], [1, 2, 2.3])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1, 2], [1u, 2, 2.3])', expected: true}")
  @TestParameters(
      "{expression: 'sets.intersects([[1], [2, 3]], [[1, 2], [2, 3.0]])', expected: true}")
  @TestParameters("{expression: 'sets.intersects([1], [\"1\", 2])', expected: false}")
  @TestParameters("{expression: 'sets.intersects([1], [1.1, 2])', expected: false}")
  @TestParameters("{expression: 'sets.intersects([1], [1.1, 2u])', expected: false}")
  public void intersects_withMixedTypes_succeeds(String expression, boolean expected)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void setsExtension_containsFunctionSubset_succeeds() throws Exception {
    CelSetsExtensions setsExtensions = CelExtensions.sets(Function.CONTAINS);
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addLibraries(setsExtensions).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(setsExtensions).build();

    Object evaluatedResult =
        celRuntime.createProgram(celCompiler.compile("sets.contains([1, 2], [2])").getAst()).eval();

    assertThat(evaluatedResult).isEqualTo(true);
  }

  @Test
  public void setsExtension_equivalentFunctionSubset_succeeds() throws Exception {
    CelSetsExtensions setsExtensions = CelExtensions.sets(Function.EQUIVALENT);
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addLibraries(setsExtensions).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(setsExtensions).build();

    Object evaluatedResult =
        celRuntime
            .createProgram(celCompiler.compile("sets.equivalent([1, 1], [1])").getAst())
            .eval();

    assertThat(evaluatedResult).isEqualTo(true);
  }

  @Test
  public void setsExtension_intersectsFunctionSubset_succeeds() throws Exception {
    CelSetsExtensions setsExtensions = CelExtensions.sets(Function.INTERSECTS);
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addLibraries(setsExtensions).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(setsExtensions).build();

    Object evaluatedResult =
        celRuntime
            .createProgram(celCompiler.compile("sets.intersects([1, 1], [1])").getAst())
            .eval();

    assertThat(evaluatedResult).isEqualTo(true);
  }

  @Test
  public void setsExtension_compileUnallowedFunction_throws() {
    CelSetsExtensions setsExtensions = CelExtensions.sets(Function.EQUIVALENT);
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addLibraries(setsExtensions).build();

    assertThrows(
        CelValidationException.class,
        () -> celCompiler.compile("sets.contains([1, 2], [2])").getAst());
  }

  @Test
  public void setsExtension_evaluateUnallowedFunction_throws() throws Exception {
    CelSetsExtensions setsExtensions = CelExtensions.sets(Function.CONTAINS, Function.EQUIVALENT);
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addLibraries(setsExtensions).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addLibraries(CelExtensions.sets(Function.EQUIVALENT))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile("sets.contains([1, 2], [2])").getAst();

    assertThrows(CelEvaluationException.class, () -> celRuntime.createProgram(ast).eval());
  }
}
