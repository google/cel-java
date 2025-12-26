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

import static dev.cel.common.Operator.NEGATE;
import static dev.cel.runtime.standard.ArithmeticHelpers.getArithmeticErrorCode;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;

/** Standard function for the negate (-) operator. */
public final class NegateOperator extends CelStandardFunction {
  private static final NegateOperator ALL_OVERLOADS = create(NegateOverload.values());

  public static NegateOperator create() {
    return ALL_OVERLOADS;
  }

  public static NegateOperator create(NegateOperator.NegateOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static NegateOperator create(Iterable<NegateOperator.NegateOverload> overloads) {
    return new NegateOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum NegateOverload implements CelStandardOverload {
    NEGATE_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "negate_int64",
                Long.class,
                (Long x) -> {
                  try {
                    return RuntimeHelpers.int64Negate(x, celOptions);
                  } catch (ArithmeticException e) {
                    throw new CelRuntimeException(e, getArithmeticErrorCode(e));
                  }
                })),
    NEGATE_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("negate_double", Double.class, (Double x) -> -x));

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    NegateOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private NegateOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(NEGATE.getFunction(), overloads);
  }
}
