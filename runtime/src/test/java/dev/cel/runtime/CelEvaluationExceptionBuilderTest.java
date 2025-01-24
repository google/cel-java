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

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelErrorCode;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelEvaluationExceptionBuilderTest {

  @Test
  public void builder_default() {
    CelEvaluationExceptionBuilder builder = CelEvaluationExceptionBuilder.newBuilder("foo");

    CelEvaluationException e = builder.build();

    assertThat(e).hasMessageThat().isEqualTo("evaluation error: foo");
    assertThat(e).hasCauseThat().isNull();
    assertThat(e.getErrorCode()).isEqualTo(CelErrorCode.INTERNAL_ERROR);
  }

  @Test
  public void builder_withoutMetadata() {
    IllegalStateException cause = new IllegalStateException("Cause");
    CelEvaluationExceptionBuilder builder =
        CelEvaluationExceptionBuilder.newBuilder("foo")
            .setCause(cause)
            .setErrorCode(CelErrorCode.ATTRIBUTE_NOT_FOUND);

    CelEvaluationException e = builder.build();

    assertThat(e).hasMessageThat().isEqualTo("evaluation error: foo");
    assertThat(e).hasCauseThat().isEqualTo(cause);
    assertThat(e.getErrorCode()).isEqualTo(CelErrorCode.ATTRIBUTE_NOT_FOUND);
  }

  @Test
  public void builder_allPropertiesSet() {
    IllegalStateException cause = new IllegalStateException("Cause");
    CelEvaluationExceptionBuilder builder =
        CelEvaluationExceptionBuilder.newBuilder("foo")
            .setCause(cause)
            .setErrorCode(CelErrorCode.BAD_FORMAT)
            .setMetadata(
                new Metadata() {
                  @Override
                  public String getLocation() {
                    return "location.txt";
                  }

                  @Override
                  public int getPosition(long exprId) {
                    return 10;
                  }
                },
                0);

    CelEvaluationException e = builder.build();

    assertThat(e).hasMessageThat().isEqualTo("evaluation error at location.txt:10: foo");
    assertThat(e).hasCauseThat().isEqualTo(cause);
    assertThat(e.getErrorCode()).isEqualTo(CelErrorCode.BAD_FORMAT);
  }
}
