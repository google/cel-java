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
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.common.types.NullableType;

/** ProtoWrapperValue represents a */
@Immutable
@AutoValue
public abstract class ProtoWrapperValue extends StructValue {

  @Override
  public abstract CelValue value();

  abstract WellKnownProto wellKnownProto();

  /**
   * Retrieves the underlying value being held in the wrapper. For example, if this is a
   * `google.protobuf.IntValue', a Java long is returned.
   */
  public Object nativeValue() {
    if (wellKnownProto().equals(WellKnownProto.BYTES_VALUE)) {
      // Return the proto ByteString as the underlying primitive value rather than a mutable byte
      // array.
      return ByteString.copyFrom(((BytesValue) value()).value().toByteArray());
    }
    return value().value();
  }

  @Override
  public abstract NullableType celType();

  @Override
  public boolean isZeroValue() {
    return value().isZeroValue();
  }

  @Override
  public CelValue select(String fieldName) {
    throw new UnsupportedOperationException("Wrappers do not support field selection");
  }

  @Override
  public boolean hasField(String fieldName) {
    throw new UnsupportedOperationException("Wrappers do not support presence tests");
  }

  public static ProtoWrapperValue create(
      MessageOrBuilder wrapperMessage, boolean enableUnsignedLongs) {
    WellKnownProto wellKnownProto = getWellKnownProtoFromWrapperName(wrapperMessage);
    CelValue celValue = newCelValueFromWrapper(wrapperMessage, wellKnownProto, enableUnsignedLongs);
    NullableType nullableType = NullableType.create(celValue.celType());
    return new AutoValue_ProtoWrapperValue(celValue, wellKnownProto, nullableType);
  }

  private static CelValue newCelValueFromWrapper(
      MessageOrBuilder message, WellKnownProto wellKnownProto, boolean enableUnsignedLongs) {
    switch (wellKnownProto) {
      case BOOL_VALUE:
        return BoolValue.create(((com.google.protobuf.BoolValue) message).getValue());
      case BYTES_VALUE:
        return BytesValue.create(
            CelByteString.of(((com.google.protobuf.BytesValue) message).getValue().toByteArray()));
      case DOUBLE_VALUE:
        return DoubleValue.create(((com.google.protobuf.DoubleValue) message).getValue());
      case FLOAT_VALUE:
        return DoubleValue.create(((FloatValue) message).getValue());
      case INT32_VALUE:
        return IntValue.create(((Int32Value) message).getValue());
      case INT64_VALUE:
        return IntValue.create(((Int64Value) message).getValue());
      case STRING_VALUE:
        return StringValue.create(((com.google.protobuf.StringValue) message).getValue());
      case UINT32_VALUE:
        return UintValue.create(((UInt32Value) message).getValue(), enableUnsignedLongs);
      case UINT64_VALUE:
        return UintValue.create(((UInt64Value) message).getValue(), enableUnsignedLongs);
      default:
        throw new IllegalArgumentException(
            "Should only be called for wrapper types. Got: " + wellKnownProto.name());
    }
  }

  private static WellKnownProto getWellKnownProtoFromWrapperName(MessageOrBuilder message) {
    WellKnownProto wellKnownProto =
        WellKnownProto.getByDescriptorName(message.getDescriptorForType().getFullName());
    if (!wellKnownProto.isWrapperType()) {
      throw new IllegalArgumentException("Expected a wrapper type. Got: " + wellKnownProto.name());
    }

    return wellKnownProto;
  }
}
