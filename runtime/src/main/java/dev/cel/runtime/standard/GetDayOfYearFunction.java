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

package dev.cel.runtime.standard;

import static dev.cel.runtime.standard.DateTimeHelpers.UTC;
import static dev.cel.runtime.standard.DateTimeHelpers.newLocalDateTime;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code getDayOfYear}. */
public final class GetDayOfYearFunction extends CelStandardFunction {

  public static GetDayOfYearFunction create(
      GetDayOfYearFunction.GetDayOfYearOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetDayOfYearFunction create(
      Iterable<GetDayOfYearFunction.GetDayOfYearOverload> overloads) {
    return new GetDayOfYearFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetDayOfYearOverload implements CelStandardOverload {
    TIMESTAMP_TO_DAY_OF_YEAR(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_day_of_year",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfYear() - 1)),
    TIMESTAMP_TO_DAY_OF_YEAR_WITH_TZ(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_day_of_year_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfYear() - 1)),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    GetDayOfYearOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private GetDayOfYearFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
