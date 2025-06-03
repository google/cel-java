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
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;

/** Standard function for {@code matches}. */
public final class MatchesFunction extends CelStandardFunction {

  public static MatchesFunction create(MatchesFunction.MatchesOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static MatchesFunction create(Iterable<MatchesFunction.MatchesOverload> overloads) {
    return new MatchesFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum MatchesOverload implements CelStandardOverload {
    MATCHES(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "matches",
                String.class,
                String.class,
                (String string, String regexp) -> {
                  try {
                    return RuntimeHelpers.matches(string, regexp, celOptions);
                  } catch (RuntimeException e) {
                    throw new CelRuntimeException(e, CelErrorCode.INVALID_ARGUMENT);
                  }
                })),
    // Duplicate receiver-style matches overload.
    MATCHES_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "matches_string",
                String.class,
                String.class,
                (String string, String regexp) -> {
                  try {
                    return RuntimeHelpers.matches(string, regexp, celOptions);
                  } catch (RuntimeException e) {
                    throw new CelRuntimeException(e, CelErrorCode.INVALID_ARGUMENT);
                  }
                })),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    MatchesOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private MatchesFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
