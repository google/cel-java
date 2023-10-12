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

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
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

/**
 * WellKnownProto types used throughout CEL. These types are specially handled to ensure that
 * bidirectional conversion between CEL native values and these well-known types is performed
 * consistently across runtimes.
 */
@Internal
public enum WellKnownProto {
  JSON_VALUE(Value.getDescriptor()),
  JSON_STRUCT_VALUE(Struct.getDescriptor()),
  JSON_LIST_VALUE(ListValue.getDescriptor()),
  ANY_VALUE(Any.getDescriptor()),
  BOOL_VALUE(BoolValue.getDescriptor(), true),
  BYTES_VALUE(BytesValue.getDescriptor(), true),
  DOUBLE_VALUE(DoubleValue.getDescriptor(), true),
  FLOAT_VALUE(FloatValue.getDescriptor(), true),
  INT32_VALUE(Int32Value.getDescriptor(), true),
  INT64_VALUE(Int64Value.getDescriptor(), true),
  STRING_VALUE(StringValue.getDescriptor(), true),
  UINT32_VALUE(UInt32Value.getDescriptor(), true),
  UINT64_VALUE(UInt64Value.getDescriptor(), true),
  DURATION_VALUE(Duration.getDescriptor()),
  TIMESTAMP_VALUE(Timestamp.getDescriptor());

  private final Descriptor descriptor;
  private final boolean isWrapperType;

  WellKnownProto(Descriptor descriptor) {
    this(descriptor, /* isWrapperType= */ false);
  }

  WellKnownProto(Descriptor descriptor, boolean isWrapperType) {
    this.descriptor = descriptor;
    this.isWrapperType = isWrapperType;
  }

  public Descriptor descriptor() {
    return descriptor;
  }

  public String typeName() {
    return descriptor.getFullName();
  }

  boolean isWrapperType() {
    return isWrapperType;
  }
}
