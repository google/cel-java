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
import com.google.common.primitives.UnsignedLong;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code double} conversion function. */
public final class DoubleFunction extends CelStandardFunction {
  private static final DoubleFunction ALL_OVERLOADS = create(DoubleOverload.values());

  public static DoubleFunction create() {
    return ALL_OVERLOADS;
  }

  public static DoubleFunction create(DoubleFunction.DoubleOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static DoubleFunction create(Iterable<DoubleFunction.DoubleOverload> overloads) {
    return new DoubleFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum DoubleOverload implements CelStandardOverload {
    DOUBLE_TO_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("double_to_double", Double.class, (Double x) -> x)),
    INT64_TO_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("int64_to_double", Long.class, Long::doubleValue)),
    STRING_TO_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "string_to_double",
                String.class,
                (String arg) -> {
                  try {
                    return Double.parseDouble(arg);
                  } catch (NumberFormatException e) {
                    throw new CelRuntimeException(e, CelErrorCode.BAD_FORMAT);
                  }
                })),
    UINT64_TO_DOUBLE(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "uint64_to_double", UnsignedLong.class, UnsignedLong::doubleValue);
          } else {
            return CelFunctionBinding.from(
                "uint64_to_double",
                Long.class,
                (Long arg) -> UnsignedLong.fromLongBits(arg).doubleValue());
          }
        }),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    DoubleOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private DoubleFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
