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

package dev.cel.common.internal;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import dev.cel.common.annotations.Internal;
import java.util.Optional;
import java.util.function.Function;

/**
 * WellKnownProto types used throughout CEL. These types are specially handled to ensure that
 * bidirectional conversion between CEL native values and these well-known types is performed
 * consistently across runtimes.
 */
@Internal
public enum WellKnownProto {
  ANY_VALUE("google.protobuf.Any", Any.class),
  DURATION("google.protobuf.Duration", Duration.class),
  JSON_LIST_VALUE("google.protobuf.ListValue", ListValue.class),
  JSON_STRUCT_VALUE("google.protobuf.Struct", Struct.class),
  JSON_VALUE("google.protobuf.Value", Value.class),
  TIMESTAMP("google.protobuf.Timestamp", Timestamp.class),
  // Wrapper types
  FLOAT_VALUE("google.protobuf.FloatValue", FloatValue.class, /* isWrapperType= */ true),
  INT32_VALUE("google.protobuf.Int32Value", Int32Value.class, /* isWrapperType= */ true),
  INT64_VALUE("google.protobuf.Int64Value", Int64Value.class, /* isWrapperType= */ true),
  STRING_VALUE(
      "google.protobuf.StringValue", StringValue.class, /* isWrapperType= */ true),
  BOOL_VALUE("google.protobuf.BoolValue", BoolValue.class, /* isWrapperType= */ true),
  BYTES_VALUE("google.protobuf.BytesValue", BytesValue.class, /* isWrapperType= */ true),
  DOUBLE_VALUE(
      "google.protobuf.DoubleValue", DoubleValue.class, /* isWrapperType= */ true),
  UINT32_VALUE(
      "google.protobuf.UInt32Value", UInt32Value.class, /* isWrapperType= */ true),
  UINT64_VALUE(
      "google.protobuf.UInt64Value", UInt64Value.class, /* isWrapperType= */ true),
  // These aren't explicitly called out as wrapper types in the spec, but behave like one, because
  // they are still converted into an equivalent primitive type.

  EMPTY("google.protobuf.Empty", Empty.class, /* isWrapperType= */ true),
  FIELD_MASK("google.protobuf.FieldMask", FieldMask.class, /* isWrapperType= */ true),
  ;

  private static final ImmutableMap<String, WellKnownProto> TYPE_NAME_TO_WELL_KNOWN_PROTO_MAP =
      stream(WellKnownProto.values())
          .collect(toImmutableMap(WellKnownProto::typeName, Function.identity()));

  private static final ImmutableMap<Class<?>, WellKnownProto> CLASS_TO_NAME_TO_WELL_KNOWN_PROTO_MAP =
      stream(WellKnownProto.values())
          .collect(toImmutableMap(WellKnownProto::messageClass, Function.identity()));

  private final String wellKnownProtoTypeName;
  private final Class<?> clazz;
  private final boolean isWrapperType;

  public String typeName() {
    return wellKnownProtoTypeName;
  }

  public Class<?> messageClass() {
    return clazz;
  }



  public static Optional<WellKnownProto> getByTypeName(String typeName) {
    return Optional.ofNullable(TYPE_NAME_TO_WELL_KNOWN_PROTO_MAP.get(typeName));
  }

  public static Optional<WellKnownProto> getByClass(Class<?> clazz) {
    return Optional.ofNullable(CLASS_TO_NAME_TO_WELL_KNOWN_PROTO_MAP.get(clazz));
  }

  /**
   * Returns true if the provided {@code typeName} is a well known type, and it's a wrapper. False otherwise.
   */
  public static boolean isWrapperType(String typeName) {
    return getByTypeName(typeName)
        .map(WellKnownProto::isWrapperType)
        .orElse(false);
  }

  public boolean isWrapperType() {
    return isWrapperType;
  }

  WellKnownProto(String wellKnownProtoTypeName, Class<?> clazz) {
    this(wellKnownProtoTypeName, clazz, /* isWrapperType= */ false);
  }

  WellKnownProto(String wellKnownProtoFullName, Class<?> clazz, boolean isWrapperType) {
    this.wellKnownProtoTypeName = wellKnownProtoFullName;
    this.clazz = clazz;
    this.isWrapperType = isWrapperType;
  }
}
