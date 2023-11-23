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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@code CelValueConverter} handles bidirectional conversion between native Java objects to {@link
 * CelValue}.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@SuppressWarnings("unchecked") // Unchecked cast of generics due to type-erasure (ex: MapValue).
@Internal
abstract class CelValueConverter {

  protected final CelOptions celOptions;

  /** Adapts a plain old Java Object to a {@link CelValue}. */
  public CelValue fromJavaObjectToCelValue(Object value) {
    if (value instanceof CelValue) {
      return (CelValue) value;
    }

    if (value instanceof Iterable) {
      return toListValue((Iterable<Object>) value);
    } else if (value instanceof Map) {
      return toMapValue((Map<Object, Object>) value);
    }

    return fromJavaPrimitiveToCelValue(value);
  }

  /** Adapts a plain old Java Object that are considered primitives to a {@link CelValue}. */
  protected CelValue fromJavaPrimitiveToCelValue(Object value) {
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
      return UintValue.create((UnsignedLong) value);
    }

    // Fall back to an Opaque value, as a custom class was supplied in the runtime. The legacy
    // interpreter allows this but this should not be allowed when a new runtime is introduced.
    // TODO: Migrate consumers to directly supply an appropriate CelValue.
    return OpaqueValue.create(value.toString(), value);
  }

  private ListValue<CelValue> toListValue(Iterable<Object> iterable) {
    ImmutableList.Builder<CelValue> listBuilder = ImmutableList.builder();
    for (Object entry : iterable) {
      listBuilder.add(fromJavaObjectToCelValue(entry));
    }

    return ImmutableListValue.create(listBuilder.build());
  }

  private MapValue<CelValue, CelValue> toMapValue(Map<Object, Object> map) {
    ImmutableMap.Builder<CelValue, CelValue> mapBuilder = ImmutableMap.builder();
    for (Entry<Object, Object> entry : map.entrySet()) {
      CelValue mapKey = fromJavaObjectToCelValue(entry.getKey());
      CelValue mapValue = fromJavaObjectToCelValue(entry.getValue());
      mapBuilder.put(mapKey, mapValue);
    }

    return ImmutableMapValue.create(mapBuilder.buildOrThrow());
  }

  protected CelValueConverter(CelOptions celOptions) {
    this.celOptions = celOptions;
  }
}
