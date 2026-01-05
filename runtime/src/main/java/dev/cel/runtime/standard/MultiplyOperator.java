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

import static dev.cel.common.Operator.MULTIPLY;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import dev.cel.common.CelOptions;
import dev.cel.common.exceptions.CelNumericOverflowException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;

/** Standard function for the multiplication (*) operator. */
public final class MultiplyOperator extends CelStandardFunction {
  private static final MultiplyOperator ALL_OVERLOADS = create(MultiplyOverload.values());

  public static MultiplyOperator create() {
    return ALL_OVERLOADS;
  }

  public static MultiplyOperator create(MultiplyOperator.MultiplyOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static MultiplyOperator create(Iterable<MultiplyOperator.MultiplyOverload> overloads) {
    return new MultiplyOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum MultiplyOverload implements CelStandardOverload {
    MULTIPLY_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "multiply_int64",
                Long.class,
                Long.class,
                (Long x, Long y) -> {
                  try {
                    return RuntimeHelpers.int64Multiply(x, y, celOptions);
                  } catch (ArithmeticException e) {
                    throw new CelNumericOverflowException(e);
                  }
                })),
    MULTIPLY_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "multiply_double", Double.class, Double.class, (Double x, Double y) -> x * y)),
    MULTIPLY_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "multiply_uint64",
                UnsignedLong.class,
                UnsignedLong.class,
                (UnsignedLong x, UnsignedLong y) -> {
                  try {
                    return RuntimeHelpers.uint64Multiply(x, y);
                  } catch (ArithmeticException e) {
                    throw new CelNumericOverflowException(e);
                  }
                });
          } else {
            return CelFunctionBinding.from(
                "multiply_uint64",
                Long.class,
                Long.class,
                (Long x, Long y) -> {
                  try {
                    return RuntimeHelpers.uint64Multiply(x, y, celOptions);
                  } catch (ArithmeticException e) {
                    throw new CelNumericOverflowException(e);
                  }
                });
          }
        });

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    MultiplyOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private MultiplyOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(MULTIPLY.getFunction(), overloads);
  }
}
