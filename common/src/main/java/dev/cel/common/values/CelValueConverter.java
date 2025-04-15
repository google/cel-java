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
import com.google.common.primitives.UnsignedLong;
import dev.cel.common.annotations.Internal;
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
abstract class CelValueConverter {

  /** Adapts a {@link CelValue} to a plain old Java Object. */
  public Object fromCelValueToJavaObject(CelValue celValue) {
    Preconditions.checkNotNull(celValue);

    if (celValue instanceof MapValue) {
      MapValue<CelValue, CelValue> mapValue = (MapValue<CelValue, CelValue>) celValue;
      ImmutableMap.Builder<Object, Object> mapBuilder = ImmutableMap.builder();
      for (Entry<CelValue, CelValue> entry : mapValue.value().entrySet()) {
        Object key = fromCelValueToJavaObject(entry.getKey());
        Object value = fromCelValueToJavaObject(entry.getValue());
        mapBuilder.put(key, value);
      }

      return mapBuilder.buildOrThrow();
    } else if (celValue instanceof ListValue) {
      ListValue<CelValue> listValue = (ListValue<CelValue>) celValue;
      ImmutableList.Builder<Object> listBuilder = ImmutableList.builder();
      for (CelValue element : listValue.value()) {
        listBuilder.add(fromCelValueToJavaObject(element));
      }
      return listBuilder.build();
    } else if (celValue instanceof OptionalValue) {
      OptionalValue<CelValue> optionalValue = (OptionalValue<CelValue>) celValue;
      if (optionalValue.isZeroValue()) {
        return Optional.empty();
      }

      return Optional.of(fromCelValueToJavaObject(optionalValue.value()));
    }

    return celValue.value();
  }

  /** Adapts a plain old Java Object to a {@link CelValue}. */
  public CelValue fromJavaObjectToCelValue(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof CelValue) {
      return (CelValue) value;
    }

    if (value instanceof Iterable) {
      return toListValue((Iterable<Object>) value);
    } else if (value instanceof Map) {
      return toMapValue((Map<Object, Object>) value);
    } else if (value instanceof Optional) {
      Optional<?> optionalValue = (Optional<?>) value;
      return optionalValue
          .map(o -> OptionalValue.create(fromJavaObjectToCelValue(o)))
          .orElse(OptionalValue.EMPTY);
    } else if (value instanceof Exception) {
      return ErrorValue.create((Exception) value);
    }

    return fromJavaPrimitiveToCelValue(value);
  }

  /** Adapts a plain old Java Object that are considered primitives to a {@link CelValue}. */
  protected CelValue fromJavaPrimitiveToCelValue(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof Boolean) {
      return BoolValue.create((Boolean) value);
    } else if (value instanceof Long) {
      return IntValue.create((Long) value);
    } else if (value instanceof Integer) {
      return IntValue.create((Integer) value);
    } else if (value instanceof String) {
      return StringValue.create((String) value);
    } else if (value instanceof byte[]) {
      return BytesValue.create(CelByteString.of((byte[]) value));
    } else if (value instanceof Double) {
      return DoubleValue.create((Double) value);
    } else if (value instanceof Float) {
      return DoubleValue.create(Double.valueOf((Float) value));
    } else if (value instanceof UnsignedLong) {
      return UintValue.create(((UnsignedLong) value).longValue(), /* enableUnsignedLongs= */ true);
    }

    // Fall back to an Opaque value, as a custom class was supplied in the runtime. The legacy
    // interpreter allows this but this should not be allowed when a new runtime is introduced.
    // TODO: Migrate consumers to directly supply an appropriate CelValue.
    return OpaqueValue.create(value.toString(), value);
  }

  private ListValue<CelValue> toListValue(Iterable<Object> iterable) {
    Preconditions.checkNotNull(iterable);

    ImmutableList.Builder<CelValue> listBuilder = ImmutableList.builder();
    for (Object entry : iterable) {
      listBuilder.add(fromJavaObjectToCelValue(entry));
    }

    return ImmutableListValue.create(listBuilder.build());
  }

  private MapValue<CelValue, CelValue> toMapValue(Map<Object, Object> map) {
    Preconditions.checkNotNull(map);

    ImmutableMap.Builder<CelValue, CelValue> mapBuilder = ImmutableMap.builder();
    for (Entry<Object, Object> entry : map.entrySet()) {
      CelValue mapKey = fromJavaObjectToCelValue(entry.getKey());
      CelValue mapValue = fromJavaObjectToCelValue(entry.getValue());
      mapBuilder.put(mapKey, mapValue);
    }

    return ImmutableMapValue.create(mapBuilder.buildOrThrow());
  }

  protected CelValueConverter() {}
}
