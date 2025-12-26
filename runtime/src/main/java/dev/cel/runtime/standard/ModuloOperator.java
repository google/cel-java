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

import static dev.cel.common.Operator.MODULO;
import static dev.cel.runtime.standard.ArithmeticHelpers.getArithmeticErrorCode;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;

/** Standard function for the modulus (%) operator. */
public final class ModuloOperator extends CelStandardFunction {
  private static final ModuloOperator ALL_OVERLOADS = create(ModuloOverload.values());

  public static ModuloOperator create() {
    return ALL_OVERLOADS;
  }

  public static ModuloOperator create(ModuloOperator.ModuloOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static ModuloOperator create(Iterable<ModuloOperator.ModuloOverload> overloads) {
    return new ModuloOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum ModuloOverload implements CelStandardOverload {
    MODULO_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "modulo_int64",
                Long.class,
                Long.class,
                (Long x, Long y) -> {
                  try {
                    return x % y;
                  } catch (ArithmeticException e) {
                    throw new CelRuntimeException(e, getArithmeticErrorCode(e));
                  }
                })),
    MODULO_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "modulo_uint64", UnsignedLong.class, UnsignedLong.class, RuntimeHelpers::uint64Mod);
          } else {
            return CelFunctionBinding.from(
                "modulo_uint64",
                Long.class,
                Long.class,
                (Long x, Long y) -> RuntimeHelpers.uint64Mod(x, y, celOptions));
          }
        }),
    ;

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    ModuloOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private ModuloOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(MODULO.getFunction(), overloads);
  }
}
