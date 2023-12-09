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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.jspecify.nullness.Nullable;

/**
 * First-class support for CEL optionals. Supports similar semantics to java.util.Optional. Also
 * supports optional field selection and presence tests against maps and structs using the optional
 * syntax.
 */
@AutoValue
@Immutable(containerOf = "E")
public abstract class OptionalValue<E extends CelValue> extends CelValue
    implements SelectableValue<CelValue> {
  private static final OptionalType OPTIONAL_TYPE = OptionalType.create(SimpleType.DYN);

  /** Sentinel value representing an empty optional ('optional.none()' in CEL) */
  public static final OptionalValue<CelValue> EMPTY = empty();

  // There is only one scenario where the value is null and it's `optional.none`.
  abstract @Nullable E innerValue();

  @Override
  public E value() {
    if (innerValue() == null) {
      throw new NoSuchElementException("No value present");
    }

    return innerValue();
  }

  @Override
  public boolean isZeroValue() {
    return innerValue() == null;
  }

  @Override
  public OptionalType celType() {
    return OPTIONAL_TYPE;
  }

  /**
   * Optional field selection on maps or structs.
   *
   * <ol>
   *   <li>msg.?field -> has(msg.field) ? optional{msg.field} : optional.none()
   *   <li>map[?key] -> key in map ? optional{map[key]} : optional.none()
   * </ol>
   */
  @Override
  public CelValue select(CelValue field) {
    return find(field).orElse(EMPTY);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<CelValue> find(CelValue field) {
    if (isZeroValue()) {
      return Optional.empty();
    }

    SelectableValue<CelValue> selectableValue = (SelectableValue<CelValue>) value();
    Optional<CelValue> selectedField = selectableValue.find(field);
    return selectedField.map(OptionalValue::create);
  }

  public static <E extends CelValue> OptionalValue<E> create(E value) {
    Preconditions.checkNotNull(value);
    return new AutoValue_OptionalValue<>(value);
  }

  private static OptionalValue<CelValue> empty() {
    return new AutoValue_OptionalValue<>(null);
  }
}
