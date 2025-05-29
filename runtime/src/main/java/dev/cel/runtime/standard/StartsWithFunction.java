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

/** Standard function for {@code startsWith}. */
public final class StartsWithFunction extends CelStandardFunction {

  public static StartsWithFunction create(StartsWithFunction.StartsWithOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static StartsWithFunction create(
      Iterable<StartsWithFunction.StartsWithOverload> overloads) {
    return new StartsWithFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum StartsWithOverload implements CelStandardOverload {
    STARTS_WITH_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "starts_with_string", String.class, String.class, String::startsWith));

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    StartsWithOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private StartsWithFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
