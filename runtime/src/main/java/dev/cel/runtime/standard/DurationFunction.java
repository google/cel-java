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
import com.google.protobuf.Duration;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;

/** Standard function for {@code duration} conversion function. */
public final class DurationFunction extends CelStandardFunction {
  private static final DurationFunction ALL_OVERLOADS = create(DurationOverload.values());

  public static DurationFunction create() {
    return ALL_OVERLOADS;
  }

  public static DurationFunction create(DurationFunction.DurationOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static DurationFunction create(Iterable<DurationFunction.DurationOverload> overloads) {
    return new DurationFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum DurationOverload implements CelStandardOverload {
    DURATION_TO_DURATION(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "duration_to_duration", java.time.Duration.class, (java.time.Duration d) -> d);
          } else {
            return CelFunctionBinding.from(
                "duration_to_duration", Duration.class, (Duration d) -> d);
          }
        }),
    STRING_TO_DURATION(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "string_to_duration",
                String.class,
                (String d) -> {
                  if (celOptions.evaluateCanonicalTypesToNativeValues()) {
                    try {
                      return RuntimeHelpers.createJavaDurationFromString(d);
                    } catch (IllegalArgumentException e) {
                      throw new CelRuntimeException(e, CelErrorCode.BAD_FORMAT);
                    }
                  } else {
                    try {
                      return RuntimeHelpers.createDurationFromString(d);
                    } catch (IllegalArgumentException e) {
                      throw new CelRuntimeException(e, CelErrorCode.BAD_FORMAT);
                    }
                  }
                })),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    DurationOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private DurationFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
