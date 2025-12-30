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

/** Standard function runtime definition for {@code getMonth}. */
public final class GetMonthFunction extends CelStandardFunction {
  private static final GetMonthFunction ALL_OVERLOADS = create(GetMonthOverload.values());

  public static GetMonthFunction create() {
    return ALL_OVERLOADS;
  }

  public static GetMonthFunction create(GetMonthFunction.GetMonthOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetMonthFunction create(Iterable<GetMonthFunction.GetMonthOverload> overloads) {
    return new GetMonthFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetMonthOverload implements CelStandardOverload {
    TIMESTAMP_TO_MONTH(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_month",
                Instant.class,
                (Instant ts) -> (long) newLocalDateTime(ts, UTC).getMonthValue() - 1);
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_month",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getMonthValue() - 1);
          }
        }),
    TIMESTAMP_TO_MONTH_WITH_TZ(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_month_with_tz",
                Instant.class,
                String.class,
                (Instant ts, String tz) -> (long) newLocalDateTime(ts, tz).getMonthValue() - 1);
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_month_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getMonthValue() - 1);
          }
        }),
    ;

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    GetMonthOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private GetMonthFunction(ImmutableSet<CelStandardOverload> overloads) {
    super("getMonth", overloads);
  }
}
