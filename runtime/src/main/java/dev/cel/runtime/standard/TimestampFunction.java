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

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.text.ParseException;
import java.util.Arrays;

/** Standard function for {@code timestamp} conversion function. */
public final class TimestampFunction extends CelStandardFunction {
  private static final TimestampFunction ALL_OVERLOADS = create(TimestampOverload.values());

  public static TimestampFunction create() {
    return ALL_OVERLOADS;
  }

  public static TimestampFunction create(TimestampFunction.TimestampOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static TimestampFunction create(Iterable<TimestampFunction.TimestampOverload> overloads) {
    return new TimestampFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum TimestampOverload implements CelStandardOverload {
    STRING_TO_TIMESTAMP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "string_to_timestamp",
                String.class,
                (String ts) -> {
                  try {
                    return ProtoTimeUtils.parse(ts);
                  } catch (ParseException e) {
                    throw new CelRuntimeException(e, CelErrorCode.BAD_FORMAT);
                  }
                })),
    TIMESTAMP_TO_TIMESTAMP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("timestamp_to_timestamp", Timestamp.class, (Timestamp x) -> x)),
    INT64_TO_TIMESTAMP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "int64_to_timestamp", Long.class, ProtoTimeUtils::fromSecondsToTimestamp)),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    TimestampOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private TimestampFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
