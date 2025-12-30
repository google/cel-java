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

import static dev.cel.common.internal.DateTimeHelpers.UTC;
import static dev.cel.common.internal.DateTimeHelpers.newLocalDateTime;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.time.Instant;
import java.util.Arrays;

/** Standard function for {@code getDayOfMonth}. */
public final class GetDayOfMonthFunction extends CelStandardFunction {
  private static final GetDayOfMonthFunction ALL_OVERLOADS = create(GetDayOfMonthOverload.values());

  public static GetDayOfMonthFunction create() {
    return ALL_OVERLOADS;
  }

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
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month",
                Instant.class,
                (Instant ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth() - 1);
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth() - 1);
          }
        }),
    TIMESTAMP_TO_DAY_OF_MONTH_WITH_TZ(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month_with_tz",
                Instant.class,
                String.class,
                (Instant ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth() - 1);
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth() - 1);
          }
        }),
    ;
    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    GetDayOfMonthOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private GetDayOfMonthFunction(ImmutableSet<CelStandardOverload> overloads) {
    super("getDayOfMonth", overloads);
  }
}
