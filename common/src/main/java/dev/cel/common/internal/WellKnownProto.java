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
import dev.cel.common.annotations.Internal;
import java.util.function.Function;

/**
 * WellKnownProto types used throughout CEL. These types are specially handled to ensure that
 * bidirectional conversion between CEL native values and these well-known types is performed
 * consistently across runtimes.
 */
@Internal
public enum WellKnownProto {
  JSON_VALUE("google.protobuf.Value"),
  JSON_STRUCT_VALUE("google.protobuf.Struct"),
  JSON_LIST_VALUE("google.protobuf.ListValue"),
  ANY_VALUE("google.protobuf.Any"),
  BOOL_VALUE("google.protobuf.BoolValue", true),
  BYTES_VALUE("google.protobuf.BytesValue", true),
  DOUBLE_VALUE("google.protobuf.DoubleValue", true),
  FLOAT_VALUE("google.protobuf.FloatValue", true),
  INT32_VALUE("google.protobuf.Int32Value", true),
  INT64_VALUE("google.protobuf.Int64Value", true),
  STRING_VALUE("google.protobuf.StringValue", true),
  UINT32_VALUE("google.protobuf.UInt32Value", true),
  UINT64_VALUE("google.protobuf.UInt64Value", true),
  DURATION_VALUE("google.protobuf.DurationValue"),
  TIMESTAMP_VALUE("google.protobuf.TimestampValue");

  private static final ImmutableMap<String, WellKnownProto> WELL_KNOWN_PROTO_MAP;

  static {
    WELL_KNOWN_PROTO_MAP =
        stream(WellKnownProto.values())
            .collect(toImmutableMap(WellKnownProto::typeName, Function.identity()));
  }

  private final String wellKnownProtoFullName;
  private final boolean isWrapperType;

  public String typeName() {
    return wellKnownProtoFullName;
  }

  public static WellKnownProto getByTypeName(String fullyQualifiedTypeName) {
    return WELL_KNOWN_PROTO_MAP.get(fullyQualifiedTypeName);
  }

  WellKnownProto(String wellKnownProtoFullName) {
    this(wellKnownProtoFullName, /* isWrapperType= */ false);
  }

  WellKnownProto(String wellKnownProtoFullName, boolean isWrapperType) {
    this.wellKnownProtoFullName = wellKnownProtoFullName;
    this.isWrapperType = isWrapperType;
  }

  public boolean isWrapperType() {
    return isWrapperType;
  }
}
