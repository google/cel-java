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

/** Standard function for {@code getDate}. */
public final class GetDateFunction extends CelStandardFunction {
  private static final GetDateFunction ALL_OVERLOADS = create(GetDateOverload.values());

  public static GetDateFunction create() {
    return ALL_OVERLOADS;
  }

  public static GetDateFunction create(GetDateFunction.GetDateOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetDateFunction create(Iterable<GetDateFunction.GetDateOverload> overloads) {
    return new GetDateFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetDateOverload implements CelStandardOverload {
    TIMESTAMP_TO_DAY_OF_MONTH_1_BASED(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month_1_based",
                Instant.class,
                (Instant ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth());
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month_1_based",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth());
          }
        }),
    TIMESTAMP_TO_DAY_OF_MONTH_1_BASED_WITH_TZ(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month_1_based_with_tz",
                Instant.class,
                String.class,
                (Instant ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth());
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_day_of_month_1_based_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth());
          }
        }),
    ;

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    GetDateOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private GetDateFunction(ImmutableSet<CelStandardOverload> overloads) {
    super("getDate", overloads);
  }
}
