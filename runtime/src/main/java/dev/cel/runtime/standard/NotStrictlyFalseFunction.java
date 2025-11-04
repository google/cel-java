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
import dev.cel.runtime.InternalFunctionBinder;
import dev.cel.runtime.RuntimeEquality;

/**
 * Standard function for {@code @not_strictly_false}. This is an internal function used within
 * comprehensions to coerce the result into true if an evaluation yields an error or an unknown set.
 */
public final class NotStrictlyFalseFunction extends CelStandardFunction {
  private static final NotStrictlyFalseFunction ALL_OVERLOADS =
      new NotStrictlyFalseFunction(ImmutableSet.copyOf(NotStrictlyFalseOverload.values()));

  public static NotStrictlyFalseFunction create() {
    return ALL_OVERLOADS;
  }

  /** Overloads for the standard function. */
  public enum NotStrictlyFalseOverload implements CelStandardOverload {
    NOT_STRICTLY_FALSE(
        (celOptions, runtimeEquality) ->
            InternalFunctionBinder.from(
                "not_strictly_false",
                Object.class,
                (Object value) -> {
                  if (value instanceof Boolean) {
                    return value;
                  }

                  return true;
                },
                /* isStrict= */ false)),
    ;

    private final CelStandardOverload bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.newFunctionBinding(celOptions, runtimeEquality);
    }

    NotStrictlyFalseOverload(CelStandardOverload bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private NotStrictlyFalseFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
