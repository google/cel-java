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

package dev.cel.common.values;

import com.google.errorprone.annotations.Immutable;

/**
 * StructValue is a representation of a structured object with typed properties.
 *
 * <p>Users may extend from this class to provide a custom struct that CEL can understand by
 * wrapping a native Java object (e.g., a POJO or a Map). Custom struct implementations must provide
 * all functionalities denoted in the CEL specification, such as field selection, presence testing
 * and new object creation.
 *
 * <p>For an expression `e` selecting a field `f`, `e.f` must throw an exception if `f` does not
 * exist in the struct (i.e: hasField returns false). If the field exists but is not set, the
 * implementation should return an appropriate default value based on the struct's semantics.
 *
 * @param <F> The type of the field identifier. Only {@code String} is supported for now, but we may
 *     extend support to other types in the future.
 * @param <V> The type of the wrapped native object.
 */
@Immutable
public abstract class StructValue<F, V> extends CelValue implements SelectableValue<F> {
  @Override
  public abstract V value();
}
