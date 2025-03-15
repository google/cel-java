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

package dev.cel.common;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * {@code CelProtoJsonAdapter} is a utility to handle conversion from Java native objects
 * representing CEL values to Protobuf's structured value which maps to JSON object schema.
 */
@Immutable
public final class CelProtoJsonAdapter {
  private static final long JSON_MAX_INT_VALUE = (1L << 53) - 1;
  private static final long JSON_MIN_INT_VALUE = -JSON_MAX_INT_VALUE;
  private static final UnsignedLong JSON_MAX_UINT_VALUE =
      UnsignedLong.fromLongBits(JSON_MAX_INT_VALUE);

  /**
   * Adapts a map to a JSON Struct.
   *
   * @throws ClassCastException If the key is not a string literal
   * @throws IllegalArgumentException If any of the map's value is not convertible to a canonical
   *     JSON representation defined by protobuf.
   */
  public static <K extends String, V> Struct adaptToJsonStructValue(Map<K, V> map) {
    Struct.Builder struct = Struct.newBuilder();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      String key = entry.getKey();
      Object keyValue = entry.getValue();

      struct.putFields(key, adaptValueToJsonValue(keyValue));
    }
    return struct.build();
  }

  /**
   * Adapts a native Java object to a JSON value.
   *
   * @throws IllegalArgumentException If the value is not convertible to a canonical JSON *
   *     representation defined by protobuf.
   */
  @SuppressWarnings("unchecked")
  public static Value adaptValueToJsonValue(Object value) {
    Value.Builder json = Value.newBuilder();
    if (value == null || value instanceof NullValue) {
      return json.setNullValue(NullValue.NULL_VALUE).build();
    }
    if (value instanceof Boolean) {
      return json.setBoolValue((Boolean) value).build();
    }
    if (value instanceof Integer || value instanceof Long) {
      long longValue = ((Number) value).longValue();
      if (longValue < JSON_MIN_INT_VALUE || longValue > JSON_MAX_INT_VALUE) {
        return json.setStringValue(Long.toString(longValue)).build();
      }
      return json.setNumberValue((double) longValue).build();
    }
    if (value instanceof UnsignedLong) {
      if (((UnsignedLong) value).compareTo(JSON_MAX_UINT_VALUE) > 0) {
        return json.setStringValue(((UnsignedLong) value).toString()).build();
      }
      return json.setNumberValue((double) ((UnsignedLong) value).longValue()).build();
    }
    if (value instanceof Float || value instanceof Double) {
      return json.setNumberValue(((Number) value).doubleValue()).build();
    }
    if (value instanceof ByteString) {
      return json.setStringValue(
              Base64.getEncoder().encodeToString(((ByteString) value).toByteArray()))
          .build();
    }
    if (value instanceof String) {
      return json.setStringValue((String) value).build();
    }
    if (value instanceof Map) {
      Struct struct = adaptToJsonStructValue((Map<String, Object>) value);
      return json.setStructValue(struct).build();
    }
    if (value instanceof Iterable) {
      ListValue listValue = adaptToJsonListValue((Iterable<Object>) value);
      return json.setListValue(listValue).build();
    }
    if (value instanceof Timestamp) {
      // CEL follows the proto3 to JSON conversion which formats as an RFC 3339 encoded JSON string.
      String ts = Timestamps.toString((Timestamp) value);
      return json.setStringValue(ts).build();
    }
    if (value instanceof Duration) {
      String duration = Durations.toString((Duration) value);
      return json.setStringValue(duration).build();
    }
    if (value instanceof FieldMask) {
      String fieldMaskStr = toJsonString((FieldMask) value);
      return json.setStringValue(fieldMaskStr).build();
    }
    if (value instanceof Empty) {
      // google.protobuf.Empty is just an empty json map {}
      return json.setStructValue(Struct.getDefaultInstance()).build();
    }

    throw new IllegalArgumentException(
        String.format("Value %s cannot be adapted to a JSON Value.", value));
  }

  /**
   * Joins the field mask's paths into a single string with commas.
   * This logic is copied from Protobuf's FieldMaskUtil.java, which we
   * cannot directly use here due to its dependency to descriptors.
   */
  private static String toJsonString(FieldMask fieldMask) {
    List<String> paths = new ArrayList<>(fieldMask.getPathsCount());

    for (String path : fieldMask.getPathsList()) {
      if (!path.isEmpty()) {
        paths.add(CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, path));
      }
    }

    return Joiner.on(",").join(paths);
  }

  /**
   * Adapts an iterable to a JSON list value.
   *
   * @throws IllegalArgumentException If any of the map's value is not convertible to a canonical
   *     JSON representation defined by protobuf.
   */
  public static <T> ListValue adaptToJsonListValue(Iterable<T> value) {
    ListValue.Builder jsonList = ListValue.newBuilder();
    for (Object elem : value) {
      jsonList.addValues(adaptValueToJsonValue(elem));
    }
    return jsonList.build();
  }

  private CelProtoJsonAdapter() {}
}
