// Copyright 2024 Google LLC
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

package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.List;

/**
 * Representation of a function overload which has been resolved to a specific set of argument types
 * and a function definition.
 */
@AutoValue
@Immutable
abstract class CelResolvedOverload implements ResolvedOverload {

  /** The overload id of the function. */
  @Override
  public abstract String getOverloadId();

  /** The types of the function parameters. */
  @Override
  public abstract ImmutableList<Class<?>> getParameterTypes();

  /* Denotes whether an overload is strict.
   *
   * <p>A strict function will not be invoked if any of its arguments are an error or unknown value.
   * The runtime automatically propagates the error or unknown instead.
   *
   * <p>A non-strict function will be invoked even if its arguments contain errors or unknowns. The
   * function's implementation is then responsible for handling these values. This is primarily used
   * for short-circuiting logical operators (e.g., `||`, `&&`) and comprehension's
   * internal @not_strictly_false function.
   *
   * <p>In a vast majority of cases, this should be set to true.
   */
  @Override
  public abstract boolean isStrict();

  /** The function definition. */
  @Override
  public abstract CelFunctionOverload getDefinition();

  /**
   * Creates a new resolved overload from the given overload id, parameter types, and definition.
   */
  public static CelResolvedOverload of(
      String overloadId,
      CelFunctionOverload definition,
      boolean isStrict,
      Class<?>... parameterTypes) {
    return of(overloadId, definition, isStrict, ImmutableList.copyOf(parameterTypes));
  }

  /**
   * Creates a new resolved overload from the given overload id, parameter types, and definition.
   */
  public static CelResolvedOverload of(
      String overloadId,
      CelFunctionOverload definition,
      boolean isStrict,
      List<Class<?>> parameterTypes) {
    return new AutoValue_CelResolvedOverload(
        overloadId, ImmutableList.copyOf(parameterTypes), isStrict, definition);
  }
}
