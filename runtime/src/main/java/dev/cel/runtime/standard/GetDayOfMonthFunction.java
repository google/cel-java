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

/** Standard function for {@code getDayOfMonth}. */
public final class GetDayOfMonthFunction extends CelStandardFunction {

  public static GetDayOfMonthFunction create(
      GetDayOfMonthFunction.GetDayOfMonthOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetDayOfMonthFunction create(
      Iterable<GetDayOfMonthFunction.GetDayOfMonthOverload> overloads) {
    return new GetDayOfMonthFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetDayOfMonthOverload implements CelStandardOverload {
    TIMESTAMP_TO_DAY_OF_MONTH(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_day_of_month",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth() - 1)),
    TIMESTAMP_TO_DAY_OF_MONTH_WITH_TZ(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_day_of_month_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth() - 1)),
    ;
    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    GetDayOfMonthOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private GetDayOfMonthFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
