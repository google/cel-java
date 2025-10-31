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
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.SafeStringFormatter;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code bool} conversion function. */
public final class BoolFunction extends CelStandardFunction {
  private static final BoolFunction ALL_OVERLOADS = create(BoolOverload.values());

  public static BoolFunction create() {
    return ALL_OVERLOADS;
  }

  public static BoolFunction create(BoolFunction.BoolOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static BoolFunction create(Iterable<BoolFunction.BoolOverload> overloads) {
    return new BoolFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum BoolOverload implements CelStandardOverload {
    BOOL_TO_BOOL(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("bool_to_bool", Boolean.class, (Boolean x) -> x)),
    STRING_TO_BOOL(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "string_to_bool",
                String.class,
                (String str) -> {
                  switch (str) {
                    case "true":
                    case "TRUE":
                    case "True":
                    case "t":
                    case "1":
                      return true;
                    case "false":
                    case "FALSE":
                    case "False":
                    case "f":
                    case "0":
                      return false;
                    default:
                      throw new CelRuntimeException(
                          new IllegalArgumentException(
                              SafeStringFormatter.format(
                                  "Type conversion error from 'string' to 'bool': [%s]", str)),
                          CelErrorCode.BAD_FORMAT);
                  }
                }));

    private final CelStandardOverload standardOverload;
    ;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    BoolOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private BoolFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
