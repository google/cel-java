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

package codelab;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Exercise1Test {
  private static final String TEST_EXPRESSION = "'Hello, World!'";

  private final Exercise1 exercise1 = new Exercise1();

  @Test
  public void compile_stringExpression_parseSuccess() {
    CelAbstractSyntaxTree ast = exercise1.compile(TEST_EXPRESSION);
    assertThat(ast).isNotNull();
  }

  @Test
  public void compile_stringExpression_typecheckSuccess() {
    CelAbstractSyntaxTree ast = exercise1.compile(TEST_EXPRESSION);
    assertThat(ast.isChecked()).isTrue();
  }

  @Test
  public void compile_booleanExpression_throwsValidationException() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> exercise1.compile("true == true"));

    assertThat(exception).hasMessageThat().contains("Failed to type-check expression.");
    assertThat(exception).hasMessageThat().contains("expected type 'string' but found 'bool'");
  }

  @Test
  public void evaluate_stringExpression_success() {
    CelAbstractSyntaxTree ast = exercise1.compile(TEST_EXPRESSION);

    Object evaluatedResult = exercise1.eval(ast);

    assertThat(evaluatedResult).isEqualTo("Hello, World!");
  }

  @Test
  public void evaluate_divideByZeroExpression_throwsEvaluationException() throws Exception {
    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = compiler.compile("1/0").getAst();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> exercise1.eval(ast));

    assertThat(exception).hasMessageThat().contains("evaluation error: / by zero");
  }
}
