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
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code getMilliseconds}. */
public final class GetMillisecondsFunction extends CelStandardFunction {
  private static final GetMillisecondsFunction ALL_OVERLOADS =
      create(GetMillisecondsOverload.values());

  public static GetMillisecondsFunction create() {
    return ALL_OVERLOADS;
  }

  public static GetMillisecondsFunction create(
      GetMillisecondsFunction.GetMillisecondsOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetMillisecondsFunction create(
      Iterable<GetMillisecondsFunction.GetMillisecondsOverload> overloads) {
    return new GetMillisecondsFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetMillisecondsOverload implements CelStandardOverload {
    // We specifically need to only access nanos-of-second field for
    // timestamp_to_milliseconds overload
    @SuppressWarnings("JavaLocalDateTimeGetNano")
    TIMESTAMP_TO_MILLISECONDS(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_milliseconds",
                Timestamp.class,
                (Timestamp ts) -> (long) (newLocalDateTime(ts, UTC).getNano() / 1e+6))),

    @SuppressWarnings("JavaLocalDateTimeGetNano")
    TIMESTAMP_TO_MILLISECONDS_WITH_TZ(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_milliseconds_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) (newLocalDateTime(ts, tz).getNano() / 1e+6))),
    DURATION_TO_MILLISECONDS(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "duration_to_milliseconds",
                Duration.class,
                (Duration arg) ->
                    ProtoTimeUtils.toMillis(arg) % java.time.Duration.ofSeconds(1).toMillis()));

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    GetMillisecondsOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private GetMillisecondsFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
