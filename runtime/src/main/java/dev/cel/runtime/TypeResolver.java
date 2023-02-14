// Copyright 2023 Google LLC
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

import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Value;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelType;
import org.jspecify.nullness.Nullable;

/**
 * The {@code TypeResolver} determines the CEL type of Java-native values and assists with adapting
 * check-time types to runtime values.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public interface TypeResolver {

  /**
   * Resolve the CEL type of the {@code obj}, using the {@code checkedTypeValue} as hint for type
   * disambiguation.
   *
   * <p>The {@code checkedTypeValue} indicates the statically determined type of the object at
   * check-time. Often, the check-time and runtime phases agree, but there are cases where the
   * runtime type is ambiguous, as is the case when a {@code Long} value is supplied as this could
   * either be an int, uint, or enum type.
   *
   * <p>Type resolution is biased toward the runtime value type, given the dynamically typed nature
   * of CEL.
   */
  @Nullable Value resolveObjectType(Object obj, @Nullable Value checkedTypeValue);

  /**
   * Adapt the check-time {@code type} instance to a runtime {@code Value}.
   *
   * <p>When the checked {@code type} does not have a runtime equivalent, e.g. {@code Type#DYN}, the
   * return value will be {@code null}.
   */
  @Nullable Value adaptType(CelType type);

  /**
   * Adapt the check-time {@code type} instance to a runtime {@code Value}.
   *
   * <p>When the checked {@code type} does not have a runtime equivalent, e.g. {@code Type#DYN}, the
   * return value will be {@code null}.
   *
   * @deprecated use {@link #adaptType(CelType)} instead. This only exists to maintain compatibility
   *     with legacy async evaluator.
   */
  @Deprecated
  @Nullable Value adaptType(@Nullable Type type);
}
