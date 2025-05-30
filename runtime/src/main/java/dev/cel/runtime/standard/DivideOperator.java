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

import static dev.cel.runtime.standard.ArithmeticHelpers.getArithmeticErrorCode;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;

/** Standard function for the division (/) operator. */
public final class DivideOperator extends CelStandardFunction {
  private static final DivideOperator ALL_OVERLOADS = create(DivideOverload.values());

  public static DivideOperator create() {
    return ALL_OVERLOADS;
  }

  public static DivideOperator create(DivideOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static DivideOperator create(Iterable<DivideOverload> overloads) {
    return new DivideOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum DivideOverload implements CelStandardOverload {
    DIVIDE_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "divide_double", Double.class, Double.class, (Double x, Double y) -> x / y)),
    DIVIDE_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "divide_int64",
                Long.class,
                Long.class,
                (Long x, Long y) -> {
                  try {
                    return RuntimeHelpers.int64Divide(x, y, celOptions);
                  } catch (ArithmeticException e) {
                    throw new CelRuntimeException(e, getArithmeticErrorCode(e));
                  }
                })),
    DIVIDE_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "divide_uint64",
                UnsignedLong.class,
                UnsignedLong.class,
                RuntimeHelpers::uint64Divide);
          } else {
            return CelFunctionBinding.from(
                "divide_uint64",
                Long.class,
                Long.class,
                (Long x, Long y) -> RuntimeHelpers.uint64Divide(x, y, celOptions));
          }
        });
    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    DivideOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private DivideOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
