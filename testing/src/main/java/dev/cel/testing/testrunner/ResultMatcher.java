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

import dev.cel.expr.ExprValue;
import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.types.CelType;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Output;
import java.util.Optional;

/** Custom result matcher for performing assertions on the result of a CEL test case. */
public interface ResultMatcher {

  /** Parameters for the result matcher. */
  @AutoValue
  public abstract class ResultMatcherParams {
    public abstract Optional<Output> expectedOutput();

    public abstract ComputedOutput computedOutput();

    /** Computed output of the CEL test case. */
    @AutoOneOf(ComputedOutput.Kind.class)
    public abstract static class ComputedOutput {
      /** Kind of the computed output. */
      public enum Kind {
        EXPR_VALUE,
        ERROR,
        UNKNOWN_SET,
      }

      public abstract Kind kind();

      public abstract ExprValue exprValue();

      public abstract CelEvaluationException error();

      public abstract ImmutableList<Long> unknownSet();

      public static ComputedOutput ofExprValue(ExprValue exprValue) {
        return AutoOneOf_ResultMatcher_ResultMatcherParams_ComputedOutput.exprValue(exprValue);
      }

      public static ComputedOutput ofError(CelEvaluationException error) {
        return AutoOneOf_ResultMatcher_ResultMatcherParams_ComputedOutput.error(error);
      }

      public static ComputedOutput ofUnknownSet(ImmutableList<Long> unknownSet) {
        return AutoOneOf_ResultMatcher_ResultMatcherParams_ComputedOutput.unknownSet(unknownSet);
      }
    }

    public abstract CelType resultType();

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_ResultMatcher_ResultMatcherParams.Builder();
    }

    /** Builder for {@link ResultMatcherParams}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setExpectedOutput(Optional<Output> result);

      public abstract Builder setResultType(CelType resultType);

      public abstract Builder setComputedOutput(ComputedOutput computedOutput);

      public abstract ResultMatcherParams build();
    }
  }

  void match(ResultMatcherParams params, Cel cel) throws Exception;
}
