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

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.runtime.CelEvaluationException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class Exercise3Test {
  private final Exercise3 exercise3 = new Exercise3();

  /**
   * Demonstrates CEL's unique feature of commutative logical operators.
   *
   * <p>If a logical operation can short-circuit a branch that results in an error, CEL evaluation
   * will return the logical result instead of propagating the error.
   */
  @Test
  @TestParameters("{expression: 'true || true', expectedResult: true}")
  @TestParameters("{expression: 'true || false', expectedResult: true}")
  @TestParameters("{expression: 'false || true', expectedResult: true}")
  @TestParameters("{expression: 'false || false', expectedResult: false}")
  @TestParameters("{expression: 'true || (1 / 0 > 2)', expectedResult: true}")
  @TestParameters("{expression: '(1 / 0 > 2) || true', expectedResult: true}")
  public void evaluate_logicalOrShortCircuits_success(String expression, boolean expectedResult) {
    Object evaluatedResult = exercise3.compileAndEvaluate(expression);

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  /** Demonstrates a case where an error is surfaced to the user. */
  @Test
  @TestParameters("{expression: 'false || (1 / 0 > 2)'}")
  @TestParameters("{expression: '(1 / 0 > 2) || false'}")
  public void evaluate_logicalOrFailure_throwsException(String expression) {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> exercise3.compileAndEvaluate(expression));

    assertThat(exception).hasMessageThat().contains("Evaluation error has occurred.");
    assertThat(exception).hasCauseThat().isInstanceOf(CelEvaluationException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("evaluation error: / by zero");
  }

  @Test
  @TestParameters("{expression: 'todo: Fill me in using logical AND! (&&)', expectedResult: true}")
  public void evaluate_logicalAndShortCircuits_success(String expression, boolean expectedResult) {
    Object evaluatedResult = exercise3.compileAndEvaluate(expression);

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expression: 'todo: Fill me in using logical AND! (&&)'}")
  public void evaluate_logicalAndFailure_throwsException(String expression) {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> exercise3.compileAndEvaluate(expression));

    assertThat(exception).hasMessageThat().contains("Evaluation error has occurred.");
    assertThat(exception).hasCauseThat().isInstanceOf(CelEvaluationException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("evaluation error: / by zero");
  }

  @Test
  @TestParameters("{expression: 'false ? (1 / 0) > 2 : false', expectedResult: false}")
  @TestParameters("{expression: 'todo: Fill me in using ternary! A ? B : C', expectedResult: true}")
  public void evaluate_ternaryShortCircuits_success(String expression, boolean expectedResult) {
    Object evaluatedResult = exercise3.compileAndEvaluate(expression);

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expression: 'todo: Fill me in using ternary! A ? B : C'}")
  public void evaluate_ternaryFailure_throwsException(String expression) {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> exercise3.compileAndEvaluate(expression));

    assertThat(exception).hasMessageThat().contains("Evaluation error has occurred.");
    assertThat(exception).hasCauseThat().isInstanceOf(CelEvaluationException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("evaluation error: / by zero");
  }
}
