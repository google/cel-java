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
import com.google.common.primitives.UnsignedLongs;
import dev.cel.common.CelOptions;
import dev.cel.common.exceptions.CelBadFormatException;
import dev.cel.common.exceptions.CelNumericOverflowException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.math.BigDecimal;
import java.util.Arrays;

/** Standard function for {@code uint} conversion function. */
public final class UintFunction extends CelStandardFunction {
  private static final UintFunction ALL_OVERLOADS = create(UintOverload.values());

  public static UintFunction create() {
    return ALL_OVERLOADS;
  }

  public static UintFunction create(UintFunction.UintOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static UintFunction create(Iterable<UintFunction.UintOverload> overloads) {
    return new UintFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum UintOverload implements CelStandardOverload {
    UINT64_TO_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "uint64_to_uint64", UnsignedLong.class, (UnsignedLong x) -> x);
          } else {
            return CelFunctionBinding.from("uint64_to_uint64", Long.class, (Long x) -> x);
          }
        }),
    INT64_TO_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "int64_to_uint64",
                Long.class,
                (Long arg) -> {
                  if (celOptions.errorOnIntWrap() && arg < 0) {
                    throw new CelNumericOverflowException("int out of uint range");
                  }
                  return UnsignedLong.valueOf(arg);
                });
          } else {
            return CelFunctionBinding.from(
                "int64_to_uint64",
                Long.class,
                (Long arg) -> {
                  if (celOptions.errorOnIntWrap() && arg < 0) {
                    throw new CelNumericOverflowException("int out of uint range");
                  }
                  return arg;
                });
          }
        }),
    DOUBLE_TO_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "double_to_uint64",
                Double.class,
                (Double arg) -> {
                  if (celOptions.errorOnIntWrap()) {
                    return RuntimeHelpers.doubleToUnsignedChecked(arg)
                        .orElseThrow(
                            () -> new CelNumericOverflowException("double out of uint range"));
                  }
                  return UnsignedLong.valueOf(BigDecimal.valueOf(arg).toBigInteger());
                });
          } else {
            return CelFunctionBinding.from(
                "double_to_uint64",
                Double.class,
                (Double arg) -> {
                  if (celOptions.errorOnIntWrap()) {
                    return RuntimeHelpers.doubleToUnsignedChecked(arg)
                        .map(UnsignedLong::longValue)
                        .orElseThrow(
                            () ->
                                new CelNumericOverflowException(
                                    "double out of uint range"));
                  }
                  return arg.longValue();
                });
          }
        }),
    STRING_TO_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "string_to_uint64",
                String.class,
                (String arg) -> {
                  try {
                    return UnsignedLong.valueOf(arg);
                  } catch (NumberFormatException e) {
                    throw new CelBadFormatException(e);
                  }
                });
          } else {
            return CelFunctionBinding.from(
                "string_to_uint64",
                String.class,
                (String arg) -> {
                  try {
                    return UnsignedLongs.parseUnsignedLong(arg);
                  } catch (NumberFormatException e) {
                    throw new CelBadFormatException(e);
                  }
                });
          }
        }),
    ;

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    UintOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private UintFunction(ImmutableSet<CelStandardOverload> overloads) {
    super("uint", overloads);
  }
}
