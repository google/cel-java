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

/** Standard function for {@code getSeconds}. */
public final class GetSecondsFunction extends CelStandardFunction {

  private static final GetSecondsFunction ALL_OVERLOADS = create(GetSecondsOverload.values());

  public static GetSecondsFunction create() {
    return ALL_OVERLOADS;
  }

  public static GetSecondsFunction create(GetSecondsFunction.GetSecondsOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetSecondsFunction create(
      Iterable<GetSecondsFunction.GetSecondsOverload> overloads) {
    return new GetSecondsFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetSecondsOverload implements CelStandardOverload {
    TIMESTAMP_TO_SECONDS(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_seconds",
                Instant.class,
                (Instant ts) -> (long) newLocalDateTime(ts, UTC).getSecond());
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_seconds",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getSecond());
          }
        }),
    TIMESTAMP_TO_SECONDS_WITH_TZ(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "timestamp_to_seconds_with_tz",
                Instant.class,
                String.class,
                (Instant ts, String tz) -> (long) newLocalDateTime(ts, tz).getSecond());
          } else {
            return CelFunctionBinding.from(
                "timestamp_to_seconds_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getSecond());
          }
        }),
    DURATION_TO_SECONDS(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "duration_to_seconds",
                java.time.Duration.class,
                dur -> {
                  long truncatedSeconds = dur.getSeconds();
                  // Preserve the existing behavior from protobuf where seconds is truncated towards
                  // 0 when negative.
                  if (dur.isNegative() && dur.getNano() > 0) {
                    truncatedSeconds++;
                  }

                  return truncatedSeconds;
                });
          } else {
            return CelFunctionBinding.from(
                "duration_to_seconds", Duration.class, ProtoTimeUtils::toSeconds);
          }
        }),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    GetSecondsOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private GetSecondsFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
