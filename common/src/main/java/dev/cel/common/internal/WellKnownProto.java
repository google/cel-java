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
import java.util.function.Function;

/**
 * WellKnownProto types used throughout CEL. These types are specially handled to ensure that
 * bidirectional conversion between CEL native values and these well-known types is performed
 * consistently across runtimes.
 */
@Internal
public enum WellKnownProto {
  JSON_VALUE("google.protobuf.Value", Value.class.getName()),
  JSON_STRUCT_VALUE("google.protobuf.Struct", Struct.class.getName()),
  JSON_LIST_VALUE("google.protobuf.ListValue", ListValue.class.getName()),
  ANY_VALUE("google.protobuf.Any", Any.class.getName()),
  BOOL_VALUE("google.protobuf.BoolValue", BoolValue.class.getName(), true),
  BYTES_VALUE("google.protobuf.BytesValue", BytesValue.class.getName(), true),
  DOUBLE_VALUE("google.protobuf.DoubleValue", DoubleValue.class.getName(), true),
  FLOAT_VALUE("google.protobuf.FloatValue", FloatValue.class.getName(), true),
  INT32_VALUE("google.protobuf.Int32Value", Int32Value.class.getName(), true),
  INT64_VALUE("google.protobuf.Int64Value", Int64Value.class.getName(), true),
  STRING_VALUE("google.protobuf.StringValue", StringValue.class.getName(), true),
  UINT32_VALUE("google.protobuf.UInt32Value", UInt32Value.class.getName(), true),
  UINT64_VALUE("google.protobuf.UInt64Value", UInt64Value.class.getName(), true),
  DURATION_VALUE("google.protobuf.DurationValue", Duration.class.getName()),
  TIMESTAMP_VALUE("google.protobuf.TimestampValue", Timestamp.class.getName());

  private static final ImmutableMap<String, WellKnownProto> WELL_KNOWN_PROTO_MAP;

  static {
    WELL_KNOWN_PROTO_MAP =
        stream(WellKnownProto.values())
            .collect(toImmutableMap(WellKnownProto::typeName, Function.identity()));
  }

  private final String wellKnownProtoFullName;
  private final String javaClassName;
  private final boolean isWrapperType;

  public String typeName() {
    return wellKnownProtoFullName;
  }

  public String javaClassName() {
    return this.javaClassName;
  }

  public static WellKnownProto getByTypeName(String fullyQualifiedTypeName) {
    return WELL_KNOWN_PROTO_MAP.get(fullyQualifiedTypeName);
  }

  WellKnownProto(String wellKnownProtoFullName, String javaClassName) {
    this(wellKnownProtoFullName, javaClassName, /* isWrapperType= */ false);
  }

  WellKnownProto(String wellKnownProtoFullName, String javaClassName, boolean isWrapperType) {
    this.wellKnownProtoFullName = wellKnownProtoFullName;
    this.javaClassName = javaClassName;
    this.isWrapperType = isWrapperType;
  }

  public boolean isWrapperType() {
    return isWrapperType;
  }
}
