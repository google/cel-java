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

  /** The function definition. */
  @Override
  public abstract CelFunctionOverload getDefinition();

  /**
   * Creates a new resolved overload from the given overload id, parameter types, and definition.
   */
  public static CelResolvedOverload of(
      String overloadId, CelFunctionOverload definition, Class<?>... parameterTypes) {
    return of(overloadId, definition, ImmutableList.copyOf(parameterTypes));
  }

  /**
   * Creates a new resolved overload from the given overload id, parameter types, and definition.
   */
  public static CelResolvedOverload of(
      String overloadId, CelFunctionOverload definition, List<Class<?>> parameterTypes) {
    return new AutoValue_CelResolvedOverload(
        overloadId, ImmutableList.copyOf(parameterTypes), definition);
  }
}
