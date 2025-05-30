// Copyright 2025 Google LLC
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

package dev.cel.testing.testrunner;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.expr.ExprValue;
import dev.cel.expr.Value;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.CelFactory;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Output;
import dev.cel.testing.testrunner.ResultMatcher.ResultMatcherParams;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class DefaultResultMatcherTest {

  private static final DefaultResultMatcher MATCHER = new DefaultResultMatcher();

  @Test
  public void match_resultExprEvaluationError_failure() throws Exception {
    ResultMatcherParams params =
        ResultMatcherParams.newBuilder()
            .setExpectedOutput(Optional.of(Output.ofResultExpr("2 / 0")))
            .setComputedOutput(
                ResultMatcherParams.ComputedOutput.ofExprValue(
                    ExprValue.newBuilder()
                        .setValue(Value.newBuilder().setInt64Value(0).build())
                        .build()))
            .setResultType(SimpleType.INT)
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> MATCHER.match(params, CelFactory.standardCelBuilder().build()));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Failed to evaluate result_expr: evaluation error at <input>:2: / by zero");
  }

  @Test
  public void match_expectedExprValueForResultExprOutputAndComputedEvalError_failure()
      throws Exception {
    ResultMatcherParams params =
        ResultMatcherParams.newBuilder()
            .setExpectedOutput(Optional.of(Output.ofResultExpr("x + y")))
            .setComputedOutput(
                ResultMatcherParams.ComputedOutput.ofError(
                    new CelEvaluationException("evaluation error")))
            .setResultType(SimpleType.INT)
            .build();

    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () -> MATCHER.match(params, CelFactory.standardCelBuilder().build()));

    assertThat(thrown).hasMessageThat().contains("Error: evaluation error");
  }

  @Test
  public void match_expectedExprValueAndComputedEvalError_failure() throws Exception {
    ResultMatcherParams params =
        ResultMatcherParams.newBuilder()
            .setExpectedOutput(
                Optional.of(
                    Output.ofResultValue(
                        ExprValue.newBuilder()
                            .setValue(Value.newBuilder().setInt64Value(3).build())
                            .build())))
            .setComputedOutput(
                ResultMatcherParams.ComputedOutput.ofError(
                    new CelEvaluationException("evaluation error")))
            .setResultType(SimpleType.INT)
            .build();

    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () -> MATCHER.match(params, CelFactory.standardCelBuilder().build()));

    assertThat(thrown).hasMessageThat().contains("Error: evaluation error");
  }

  @Test
  public void match_expectedEvalErrorAndComputedExprValue_failure() throws Exception {
    ResultMatcherParams params =
        ResultMatcherParams.newBuilder()
            .setExpectedOutput(
                Optional.of(Output.ofEvalError(ImmutableList.of("evaluation error"))))
            .setComputedOutput(
                ResultMatcherParams.ComputedOutput.ofExprValue(
                    ExprValue.newBuilder()
                        .setValue(Value.newBuilder().setInt64Value(3).build())
                        .build()))
            .setResultType(SimpleType.INT)
            .build();

    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () -> MATCHER.match(params, CelFactory.standardCelBuilder().build()));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Evaluation was successful but no value was provided. Computed output:");
    assertThat(thrown).hasMessageThat().contains("value {\n  int64_value: 3\n}");
  }
}
