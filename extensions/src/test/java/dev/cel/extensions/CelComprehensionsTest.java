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

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelComprehensionsTest {
  private static final CelOptions CEL_OPTIONS = CelOptions.current().build();
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .setOptions(CEL_OPTIONS)
          .addLibraries(CelExtensions.comprehensions())
          .build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(CEL_OPTIONS)
          .addLibraries(CelExtensions.comprehensions())
          .build();

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum MapInsertTestCase {
    EMPTY_MAP("cel.mapInsert({}, {})", ImmutableMap.of()),
    EMPTY_FULL_MAP("cel.mapInsert({}, {2.0: 3.0})", ImmutableMap.of(2.0, 3.0)),
    DOUBLE_MAP("cel.mapInsert({1.0: 5.0}, {2.0: 3.0})", ImmutableMap.of(1.0, 5.0, 2.0, 3.0)),
    INT_MAP("cel.mapInsert({1: 5}, {2: 3})", ImmutableMap.of(1L, 5L, 2L, 3L)),
    LONG_MAP("cel.mapInsert({1: 5}, {2: 3})", ImmutableMap.of(1L, 5L, 2L, 3L)),
    MIXED_INPUT_MAP("cel.mapInsert({5.0: 30}, {2.0: 30})", ImmutableMap.of(5.0, 30L, 2.0, 30L)),
    SIMPLE_MAP_INSERT("cel.mapInsert({'a': 7}, 'b', 3)", ImmutableMap.of("a", 7L, "b", 3L)),
    NESTED_MAP_INSERT(
        "cel.mapInsert({'a': {1: 5}}, 'b', {1: 3})",
        ImmutableMap.of("a", ImmutableMap.of(1L, 5L), "b", ImmutableMap.of(1L, 3L)));

    private final String expr;
    private final Map<Object, Object> expectedResult;

    MapInsertTestCase(String expr, Map<Object, Object> expectedResult) {
      this.expr = expr;
      this.expectedResult = expectedResult;
    }
  }

  @Test
  public void mapInsert_success(@TestParameter MapInsertTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(testCase.expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(Objects.equals(result, testCase.expectedResult)).isTrue();
  }

  @Test
  @TestParameters("{expr: 'cel.mapInsert()'}")
  @TestParameters("{expr: 'cel.mapInsert({})'}")
  @TestParameters("{expr: 'cel.mapInsert({1: 5}, 1, 3, 13, 72})'}")
  public void mapInsert_invalidSizeArgs_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e)
        .hasMessageThat()
        .contains(
            "cel.mapInsert() arguments must be either two maps or a map and a key-value pair");
  }

  @Test
  @TestParameters("{expr: 'cel.mapInsert({1: 5}, {1: 3}, {1: 3})'}")
  @TestParameters("{expr: 'cel.mapInsert({1: 21}, [1], 3)'}")
  public void mapInsertMapKeyValue_invalidKeyArgs_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e).hasMessageThat().contains("is an invalid Key");
  }

  @Test
  @TestParameters("{expr: 'cel.mapInsert(1, 1, 3)'}")
  @TestParameters("{expr: 'cel.mapInsert([1], 1, 3)'}")
  public void mapInsertMapKeyValue_invalidMapArgs_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e).hasMessageThat().contains("must be a map");
  }

  @Test
  @TestParameters("{expr: 'cel.mapInsert(1, {1: 2})'}")
  @TestParameters("{expr: 'cel.mapInsert({1:[2]}, 3)'}")
  public void mapInsertMapMap_invalidMapArgs_throwsCompilationException(String expr) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e).hasMessageThat().contains("must be a map");
  }

  @Test
  @TestParameters("{expr: 'cel.mapInsert({1: 5},{1: 3})'}")
  @TestParameters("{expr: 'cel.mapInsert({1: 5}, 1, 3)'}")
  public void mapInsert_sameKey_throwsRuntimeException(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> CEL_RUNTIME.createProgram(ast).eval());

    assertThat(e).hasMessageThat().contains("evaluation error");
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("insert failed: key '1' already exists");
  }
}
