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
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code endsWith}. */
public final class EndsWithFunction extends CelStandardFunction {
  private static final EndsWithFunction ALL_OVERLOADS = create(EndsWithOverload.values());

  public static EndsWithFunction create() {
    return ALL_OVERLOADS;
  }

  public static EndsWithFunction create(EndsWithFunction.EndsWithOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static EndsWithFunction create(Iterable<EndsWithFunction.EndsWithOverload> overloads) {
    return new EndsWithFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum EndsWithOverload implements CelStandardOverload {
    ENDS_WITH_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "ends_with_string", String.class, String.class, String::endsWith)),
    ;

    private final CelStandardOverload standardOverload;
    ;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    EndsWithOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private EndsWithFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
