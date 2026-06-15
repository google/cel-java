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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.OpaqueType;

/**
 * OpaqueValue is the value representation of an {@link OpaqueType}.
 *
 * <p>Users may provide a custom opaque type that CEL can understand. Note that this is only
 * supported for the Planner runtime. There are two primary modes of extending this class:
 *
 * <ul>
 *   <li><b>Direct Extension (Recommended):</b> A domain object directly extends {@code OpaqueValue}
 *       and returns {@code this} for its {@link #value()} method. This approach allows the CEL
 *       engine to evaluate the object natively without stripping its type information, eliminating
 *       the need to register a custom {@link CelValueConverter}.
 *   <li><b>Wrapping:</b> A domain object is wrapped into an {@code OpaqueValue} via the {@link
 *       #create(String, Object)} factory method. This is required when users cannot modify their
 *       existing POJOs to extend {@code OpaqueValue}. However, because the CEL runtime aggressively
 *       unwraps objects during evaluation, this mode necessitates implementing and registering a
 *       custom {@code CelValueConverter} that maps the unwrapped native Java object back into its
 *       corresponding {@code OpaqueValue}.
 * </ul>
 */
@Immutable
public abstract class OpaqueValue extends CelValue {

  @Override
  public boolean isZeroValue() {
    return false;
  }

  @Override
  public abstract OpaqueType celType();

  /**
   * Creates an {@code OpaqueValue} by wrapping a domain object.
   *
   * <p>This method should only be used for the "Wrapping" extension mode (see class Javadoc) when
   * users cannot modify their POJOs to directly extend {@code OpaqueValue}. Using this method
   * necessitates implementing and registering a custom {@link CelValueConverter}.
   *
   * @param name The name of the opaque type.
   * @param value The raw Java object to wrap.
   */
  public static OpaqueValue create(String name, Object value) {
    return new AutoValue_OpaqueValue_OpaqueValueWrapper(
        checkNotNull(value), OpaqueType.create(name));
  }

  @AutoValue
  @AutoValue.CopyAnnotations
  @Immutable
  @SuppressWarnings("Immutable")
  abstract static class OpaqueValueWrapper extends OpaqueValue {
    @Override
    public abstract Object value();

    @Override
    public abstract OpaqueType celType();
  }
}
