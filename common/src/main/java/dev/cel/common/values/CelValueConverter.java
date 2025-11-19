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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * {@code CelValueConverter} handles bidirectional conversion between native Java objects to {@link
 * CelValue}.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@SuppressWarnings("unchecked") // Unchecked cast of generics due to type-erasure (ex: MapValue).
@Internal
@Immutable
abstract class CelValueConverter {

  /** Adapts a {@link CelValue} to a plain old Java Object. */
  public Object unwrap(CelValue celValue) {
    Preconditions.checkNotNull(celValue);

    if (celValue instanceof OptionalValue) {
      OptionalValue<Object, Object> optionalValue = (OptionalValue<Object, Object>) celValue;
      if (optionalValue.isZeroValue()) {
        return Optional.empty();
      }

      return Optional.of(optionalValue.value());
    }

    return celValue.value();
  }

  /**
   * Canonicalizes an inbound {@code value} into a suitable Java object representation for
   * evaluation.
   */
  public Object toRuntimeValue(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof CelValue) {
      return value;
    }

    if (value instanceof Collection) {
      return toListValue((Collection<Object>) value);
    } else if (value instanceof Map) {
      return toMapValue((Map<Object, Object>) value);
    } else if (value instanceof Optional) {
      Optional<Object> optionalValue = (Optional<Object>) value;
      return optionalValue
          .map(this::toRuntimeValue)
          .map(OptionalValue::create)
          .orElse(OptionalValue.EMPTY);
    } else if (value instanceof Exception) {
      return ErrorValue.create((Exception) value);
    }

    return normalizePrimitive(value);
  }

  protected Object normalizePrimitive(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof Integer) {
      return ((Integer) value).longValue();
    } else if (value instanceof byte[]) {
      return CelByteString.of((byte[]) value);
    } else if (value instanceof Float) {
      return ((Float) value).doubleValue();
    }

    return value;
  }

  private ImmutableList<Object> toListValue(Collection<Object> iterable) {
    Preconditions.checkNotNull(iterable);

    ImmutableList.Builder<Object> listBuilder =
        ImmutableList.builderWithExpectedSize(iterable.size());
    for (Object entry : iterable) {
      listBuilder.add(toRuntimeValue(entry));
    }

    return listBuilder.build();
  }

  private ImmutableMap<Object, Object> toMapValue(Map<Object, Object> map) {
    Preconditions.checkNotNull(map);

    ImmutableMap.Builder<Object, Object> mapBuilder =
        ImmutableMap.builderWithExpectedSize(map.size());
    for (Entry<Object, Object> entry : map.entrySet()) {
      Object mapKey = toRuntimeValue(entry.getKey());
      Object mapValue = toRuntimeValue(entry.getValue());
      mapBuilder.put(mapKey, mapValue);
    }

    return mapBuilder.buildOrThrow();
  }

  protected CelValueConverter() {}
}
