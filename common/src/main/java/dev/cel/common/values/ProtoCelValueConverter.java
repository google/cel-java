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
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.common.types.CelTypes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ProtoCelValueConverter} handles bidirectional conversion between native Java and protobuf
 * objects to {@link CelValue}. This converter leverages descriptors, thus requires the full version
 * of protobuf implementation.
 *
 * <p>Protobuf semantics take precedence for conversion. For example, CEL's TimestampValue will be
 * converted into Protobuf's Timestamp instead of java.time.Instant.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class ProtoCelValueConverter extends BaseProtoCelValueConverter {
  private final CelDescriptorPool celDescriptorPool;
  private final DynamicProto dynamicProto;

  /** Constructs a new instance of ProtoCelValueConverter. */
  public static ProtoCelValueConverter newInstance(
      CelDescriptorPool celDescriptorPool, DynamicProto dynamicProto) {
    return new ProtoCelValueConverter(celDescriptorPool, dynamicProto);
  }

  /** Adapts a Protobuf message into a {@link CelValue}. */
  public CelValue fromProtoMessageToCelValue(MessageOrBuilder message) {
    Preconditions.checkNotNull(message);

    // Attempt to convert the proto from a dynamic message into a concrete message if possible.
    if (message instanceof DynamicMessage) {
      message = dynamicProto.maybeAdaptDynamicMessage((DynamicMessage) message);
    }

    WellKnownProto wellKnownProto =
        WellKnownProto.getByTypeName(message.getDescriptorForType().getFullName());
    if (wellKnownProto == null) {
      return ProtoMessageValue.create((Message) message, celDescriptorPool, this);
    }

    switch (wellKnownProto) {
      case ANY_VALUE:
        Message unpackedMessage;
        try {
          unpackedMessage = dynamicProto.unpack((Any) message);
        } catch (InvalidProtocolBufferException e) {
          throw new IllegalStateException(
              "Unpacking failed for message: " + message.getDescriptorForType().getFullName(), e);
        }
        return fromProtoMessageToCelValue(unpackedMessage);
      default:
        return super.fromWellKnownProtoToCelValue(message, wellKnownProto);
    }
  }

  @Override
  public CelValue fromProtoMessageToCelValue(String unusedProtoTypeName, MessageLite msg) {
    return fromProtoMessageToCelValue((MessageOrBuilder) msg);
  }
  /**
   * Adapts a plain old Java Object to a {@link CelValue}. Protobuf semantics take precedence for
   * conversion.
   */
  @Override
  public CelValue fromJavaObjectToCelValue(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof Message) {
      return fromProtoMessageToCelValue((Message) value);
    } else if (value instanceof Message.Builder) {
      Message.Builder msgBuilder = (Message.Builder) value;
      return fromProtoMessageToCelValue(msgBuilder.build());
    } else if (value instanceof EnumValueDescriptor) {
      // (b/178627883) Strongly typed enum is not supported yet
      return IntValue.create(((EnumValueDescriptor) value).getNumber());
    }

    return super.fromJavaObjectToCelValue(value);
  }

  /** Adapts the protobuf message field into {@link CelValue}. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public CelValue fromProtoMessageFieldToCelValue(
      Message message, FieldDescriptor fieldDescriptor) {
    Preconditions.checkNotNull(message);
    Preconditions.checkNotNull(fieldDescriptor);

    Object result = message.getField(fieldDescriptor);
    switch (fieldDescriptor.getType()) {
      case MESSAGE:
        if (CelTypes.isWrapperType(fieldDescriptor.getMessageType().getFullName())
            && !message.hasField(fieldDescriptor)) {
          // Special semantics for wrapper types per CEL specification. These all convert into null
          // instead of the default value.
          return NullValue.NULL_VALUE;
        } else if (fieldDescriptor.isMapField()) {
          Map<Object, Object> map = new HashMap<>();
          Object mapKey;
          Object mapValue;
          for (Object entry : ((List<Object>) result)) {
            if (entry instanceof MapEntry) {
              MapEntry mapEntry = (MapEntry) entry;
              mapKey = mapEntry.getKey();
              mapValue = mapEntry.getValue();
            } else if (entry instanceof DynamicMessage) {
              DynamicMessage dynamicMessage = (DynamicMessage) entry;
              FieldDescriptor keyFieldDescriptor =
                  fieldDescriptor.getMessageType().findFieldByNumber(1);
              FieldDescriptor valueFieldDescriptor =
                  fieldDescriptor.getMessageType().findFieldByNumber(2);
              mapKey = dynamicMessage.getField(keyFieldDescriptor);
              mapValue = dynamicMessage.getField(valueFieldDescriptor);
            } else {
              throw new IllegalStateException("Unexpected map field type: " + entry);
            }

            map.put(mapKey, mapValue);
          }
          return fromJavaObjectToCelValue(map);
        }
        break;
      case UINT32:
        return UintValue.create((int) result, true);
      case UINT64:
        return UintValue.create((long) result, true);
      default:
        break;
    }

    return fromJavaObjectToCelValue(result);
  }

  private ProtoCelValueConverter(
      CelDescriptorPool celDescriptorPool, DynamicProto dynamicProto) {
    Preconditions.checkNotNull(celDescriptorPool);
    Preconditions.checkNotNull(dynamicProto);
    this.celDescriptorPool = celDescriptorPool;
    this.dynamicProto = dynamicProto;
  }
}
