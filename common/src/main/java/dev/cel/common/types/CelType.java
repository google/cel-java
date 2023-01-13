// Copyright 2022 Google LLC
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

package dev.cel.common.types;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.function.Function;

/**
 * Abstract representation of a CEL type which indicates its {@code CelKind}, {@code name}, and
 * {@code parameters}.
 *
 * <p>Each type also exposes functions to generate a copy of the type containing fresh type
 * variables, and a function for generating a new instance of the type with a different set of type
 * parameters. These functions are critical for making type inferences.
 */
@Immutable
@CheckReturnValue
public abstract class CelType {

  protected CelType() {}

  /** Return the type {@code CelKind}. */
  public abstract CelKind kind();

  /**
   * Return the type name.
   *
   * <p>For struct types this should be the fully qualified name. Be wary of introducing unqualified
   * type names as they may collide with future CEL type.
   */
  public abstract String name();

  /** Return the type parameters. e.g. a map's key and value {@code CelType}. */
  public ImmutableList<CelType> parameters() {
    return ImmutableList.of();
  }

  /**
   * Determine whether {@code this} type is assignable from the {@code other} type value.
   *
   * <p>Defaults to an equality test.
   */
  public boolean isAssignableFrom(CelType other) {
    return this.equals(other);
  }

  /**
   * Instantiate a new copy of this type with alternative {@code parameters}.
   *
   * <p>If the {@code CelType} does not have any {@code parameters}, then the return value defaults
   * to the original type instance.
   */
  @CanIgnoreReturnValue
  public CelType withParameters(ImmutableList<CelType> parameters) {
    return this;
  }

  /**
   * Instantiate a new copy of this type with alternative {@code parameters}.
   *
   * <p>If the {@code CelType} does not have any {@code parameters}, then the return value defaults
   * to the original type instance.
   */
  @CanIgnoreReturnValue
  public CelType withFreshTypeParamVariables(Function<String, String> varNameGenerator) {
    return withParameters(
        parameters().stream()
            .map(p -> p.withFreshTypeParamVariables(varNameGenerator))
            .collect(toImmutableList()));
  }
}
