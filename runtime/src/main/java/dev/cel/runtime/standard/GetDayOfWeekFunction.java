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
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;

/** Standard function for {@code getDayOfWeek}. */
public final class GetDayOfWeekFunction extends CelStandardFunction {
  private static final GetDayOfWeekFunction ALL_OVERLOADS = create(GetDayOfWeekOverload.values());

  public static GetDayOfWeekFunction create() {
    return ALL_OVERLOADS;
  }

  public static GetDayOfWeekFunction create(
      GetDayOfWeekFunction.GetDayOfWeekOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetDayOfWeekFunction create(
      Iterable<GetDayOfWeekFunction.GetDayOfWeekOverload> overloads) {
    return new GetDayOfWeekFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetDayOfWeekOverload implements CelStandardOverload {
    TIMESTAMP_TO_DAY_OF_WEEK(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_week",
                Instant.class,
                (Instant ts) -> {
                  // CEL treats Sunday as day 0, but Java.time treats it as day 7.
                  DayOfWeek dayOfWeek = newLocalDateTime(ts, UTC).getDayOfWeek();
                  return (long) dayOfWeek.getValue() % 7;
                });
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_week",
                Timestamp.class,
                (Timestamp ts) -> {
                  // CEL treats Sunday as day 0, but Java.time treats it as day 7.
                  DayOfWeek dayOfWeek = newLocalDateTime(ts, UTC).getDayOfWeek();
                  return (long) dayOfWeek.getValue() % 7;
                });
          }
        }),
    TIMESTAMP_TO_DAY_OF_WEEK_WITH_TZ(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_week_with_tz",
                Instant.class,
                String.class,
                (Instant ts, String tz) -> {
                  // CEL treats Sunday as day 0, but Java.time treats it as day 7.
                  DayOfWeek dayOfWeek = newLocalDateTime(ts, tz).getDayOfWeek();
                  return (long) dayOfWeek.getValue() % 7;
                });
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_week_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> {
                  // CEL treats Sunday as day 0, but Java.time treats it as day 7.
                  DayOfWeek dayOfWeek = newLocalDateTime(ts, tz).getDayOfWeek();
                  return (long) dayOfWeek.getValue() % 7;
                });
          }
        });
    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    GetDayOfWeekOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private GetDayOfWeekFunction(ImmutableSet<CelStandardOverload> overloads) {
    super("getDayOfWeek", overloads);
  }
}
