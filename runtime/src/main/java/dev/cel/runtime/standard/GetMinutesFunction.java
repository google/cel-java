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

/** Standard function for {@code getMinutes}. */
public final class GetMinutesFunction extends CelStandardFunction {

  public static GetMinutesFunction create(GetMinutesFunction.GetMinutesOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetMinutesFunction create(
      Iterable<GetMinutesFunction.GetMinutesOverload> overloads) {
    return new GetMinutesFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetMinutesOverload implements CelStandardOverload {
    TIMESTAMP_TO_MINUTES(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_minutes",
                Timestamp.class,
                (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getMinute())),
    TIMESTAMP_TO_MINUTES_WITH_TZ(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_minutes_with_tz",
                Timestamp.class,
                String.class,
                (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getMinute())),
    DURATION_TO_MINUTES(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "duration_to_minutes", Duration.class, ProtoTimeUtils::toMinutes)),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    GetMinutesOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private GetMinutesFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
