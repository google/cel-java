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
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.time.Instant;
import java.util.Arrays;

/** Standard function for {@code getHours}. */
public final class GetHoursFunction extends CelStandardFunction {
  private static final GetHoursFunction ALL_OVERLOADS = create(GetHoursOverload.values());

  public static GetHoursFunction create() {
    return ALL_OVERLOADS;
  }

  public static GetHoursFunction create(GetHoursFunction.GetHoursOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetHoursFunction create(Iterable<GetHoursFunction.GetHoursOverload> overloads) {
    return new GetHoursFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetHoursOverload implements CelStandardOverload {
    TIMESTAMP_TO_HOURS(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_hours",
                Instant.class,
                (Instant ts) -> (long) newLocalDateTime(ts, UTC).getHour());
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_hours",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getHour());
          }
        }),
    TIMESTAMP_TO_HOURS_WITH_TZ(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_hours_with_tz",
                Instant.class,
                String.class,
                (Instant ts, String tz) -> (long) newLocalDateTime(ts, tz).getHour());
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_hours_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getHour());
          }
        }),
    DURATION_TO_HOURS(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "duration_to_hours", java.time.Duration.class, java.time.Duration::toHours);
          } else {
            return CelFunctionBinding.from(
                "duration_to_hours", Duration.class, ProtoTimeUtils::toHours);
          }
        }),
    ;

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    GetHoursOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private GetHoursFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
