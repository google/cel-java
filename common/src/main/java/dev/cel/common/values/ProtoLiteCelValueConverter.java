// Copyright 2025 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Defaults;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.MessageLite;
import com.google.protobuf.NullValue;
import com.google.protobuf.WireFormat;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.CelFieldValueType;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * {@code ProtoLiteCelValueConverter} handles bidirectional conversion between native Java and
 * protobuf objects to {@link CelValue}. This converter is specifically designed for use with
 * lite-variants of protobuf messages.
 *
 * <p>Protobuf semantics take precedence for conversion. For example, CEL's TimestampValue will be
 * converted into Protobuf's Timestamp instead of java.time.Instant.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class ProtoLiteCelValueConverter extends BaseProtoCelValueConverter {
  private final CelLiteDescriptorPool descriptorPool;

  public static ProtoLiteCelValueConverter newInstance(
      CelLiteDescriptorPool celLiteDescriptorPool) {
    return new ProtoLiteCelValueConverter(celLiteDescriptorPool);
  }

  private static Object readPrimitiveField(CodedInputStream inputStream, FieldLiteDescriptor.Type fieldType)
      throws IOException {
    switch (fieldType) {
      case SINT32:
        return inputStream.readSInt32();
      case SINT64:
        return inputStream.readSInt64();
      case INT32:
      case ENUM:
        return inputStream.readInt32();
      case INT64:
        return inputStream.readInt64();
      case UINT32:
        return inputStream.readUInt32();
      case UINT64:
        return inputStream.readUInt64();
      case BOOL:
        return inputStream.readBool();
      case STRING:
        return inputStream.readStringRequireUtf8();
      default:
        throw new IllegalStateException("Unexpected field type: " + fieldType);
    }
  }

  private static Object readFixed32BitField(CodedInputStream inputStream, FieldLiteDescriptor fieldDescriptor) throws IOException {
    switch (fieldDescriptor.getProtoFieldType()) {
      case FLOAT:
        return inputStream.readFloat();
      case FIXED32:
      case SFIXED32:
        return inputStream.readRawLittleEndian32();
      default:
        throw new IllegalStateException("Unexpected field type: " + fieldDescriptor.getProtoFieldType());
    }
  }

  private static Object readFixed64BitField(CodedInputStream inputStream, FieldLiteDescriptor fieldDescriptor) throws IOException {
    switch (fieldDescriptor.getProtoFieldType()) {
      case DOUBLE:
        return inputStream.readDouble();
      case FIXED64:
      case SFIXED64:
        return inputStream.readRawLittleEndian64();
      default:
        throw new IllegalStateException("Unexpected field type: " + fieldDescriptor.getProtoFieldType());
    }
  }

  private Object readLengthDelimitedField(CodedInputStream inputStream, FieldLiteDescriptor fieldDescriptor) throws IOException {
    FieldLiteDescriptor.Type fieldType = fieldDescriptor.getProtoFieldType();

    switch (fieldType) {
      case BYTES:
        return inputStream.readBytes();
      case MESSAGE:
        MessageLite.Builder builder = descriptorPool.findDescriptor(fieldDescriptor.getFieldProtoTypeName())
            .map(MessageLiteDescriptor::newMessageBuilder)
            .orElse(null);

        if (builder != null) {
          inputStream.readMessage(builder, ExtensionRegistryLite.getEmptyRegistry());
          return builder.build();
        } else {
          // This is typically not very useful
          return inputStream.readBytes();
        }
      case STRING:
        return inputStream.readStringRequireUtf8();
      default:
        throw new IllegalStateException("Unexpected field type: " + fieldType);
    }
  }

  private static Object getDefaultValue(FieldLiteDescriptor fieldDescriptor) {
    FieldLiteDescriptor.JavaType type = fieldDescriptor.getJavaType();
    switch (type) {
      case INT:
        return fieldDescriptor.getProtoFieldType().equals(FieldLiteDescriptor.Type.UINT32) ? UnsignedLong.ZERO : Defaults.defaultValue(long.class);
      case LONG:
        return fieldDescriptor.getProtoFieldType().equals(FieldLiteDescriptor.Type.UINT64) ? UnsignedLong.ZERO : Defaults.defaultValue(long.class);
      case FLOAT:
        return Defaults.defaultValue(float.class);
      case DOUBLE:
        return Defaults.defaultValue(double.class);
      case BOOLEAN:
        return Defaults.defaultValue(boolean.class);
      case STRING:
        return "";
      case BYTE_STRING:
        return ByteString.EMPTY;
      case ENUM: // Ordinarily, an enum value descriptor is returned for this one. We'll need a different representation here.
        throw new UnsupportedOperationException("Not yet implemented");
      case MESSAGE:
        if (WellKnownProto.isWrapperType(fieldDescriptor.getFieldProtoTypeName())) {
          return NullValue.NULL_VALUE;
        } else {
          throw new UnsupportedOperationException("Not yet implemented");
        }
      default:
        throw new IllegalStateException("Unexpected java type: " + type);
    }
  }

  private List<Object> readPackedRepeatedFields(CodedInputStream inputStream, FieldLiteDescriptor fieldDescriptor)
      throws IOException {
    int length = inputStream.readInt32();
    int limit = inputStream.pushLimit(length);
    List<Object> repeatedFieldValues = new ArrayList<>();
    while (inputStream.getBytesUntilLimit() > 0) {
      Object value = readPrimitiveField(inputStream, fieldDescriptor.getProtoFieldType());
      repeatedFieldValues.add(value);
    }
    inputStream.popLimit(limit);
    return Collections.unmodifiableList(repeatedFieldValues);
  }

  private ImmutableMap<Object, Object> readSingleMapEntry(CodedInputStream inputStream, FieldLiteDescriptor fieldDescriptor) throws IOException {
    ImmutableMap<String, Object> singleMapEntry = readAllFields(inputStream.readByteArray(), fieldDescriptor.getFieldProtoTypeName());
    Object key = checkNotNull(singleMapEntry.get("key"));
    Object value = checkNotNull(singleMapEntry.get("value"));
    return ImmutableMap.of(key, value);
  }

  private ImmutableMap<String, Object> readAllFields(byte[] bytes, String protoTypeName)
      throws IOException {
    MessageLiteDescriptor messageDescriptor = descriptorPool.getDescriptorOrThrow(protoTypeName);
    CodedInputStream inputStream = CodedInputStream.newInstance(bytes);

    ImmutableMap.Builder<String, Object> fieldValues = ImmutableMap.builder();
    Map<Integer, List<Object>> nonPackedRepeatedFields = new HashMap<>();
    Map<Integer, Map<Object, Object>> mapFieldValues = new HashMap<>();
    for (int iterCount = 0; iterCount < bytes.length; iterCount++) {
      int tag = inputStream.readTag();
      if (tag == 0) {
        break;
      }

      int tagWireType = WireFormat.getTagWireType(tag);
      int fieldNumber = WireFormat.getTagFieldNumber(tag);
      FieldLiteDescriptor fieldDescriptor = messageDescriptor.getByFieldNumberOrThrow(fieldNumber);

      Object payload;
      switch (tagWireType) {
        case WireFormat.WIRETYPE_VARINT:
          payload = readPrimitiveField(inputStream, fieldDescriptor.getProtoFieldType());
          break;
        case WireFormat.WIRETYPE_FIXED32:
          payload = readFixed32BitField(inputStream, fieldDescriptor);
          break;
        case WireFormat.WIRETYPE_FIXED64:
          payload = readFixed64BitField(inputStream, fieldDescriptor);
          break;
        case WireFormat.WIRETYPE_LENGTH_DELIMITED:
          if (fieldDescriptor.getCelFieldValueType().equals(CelFieldValueType.LIST)) {
            if (fieldDescriptor.getIsPacked()) {
              payload = readPackedRepeatedFields(inputStream, fieldDescriptor);
            } else {
              List<Object> repeatedValues = nonPackedRepeatedFields.computeIfAbsent(fieldNumber, (unused) -> new ArrayList<>());
              repeatedValues.add(readPrimitiveField(inputStream, fieldDescriptor.getProtoFieldType()));
              payload = repeatedValues;
            }
          } else if (fieldDescriptor.getCelFieldValueType().equals(CelFieldValueType.MAP)){
            Map<Object, Object> fieldMap = mapFieldValues.computeIfAbsent(fieldNumber, (unused) -> new HashMap<>());
            fieldMap.putAll(readSingleMapEntry(inputStream, fieldDescriptor));
            payload = fieldMap;
          } else {
            payload = readLengthDelimitedField(inputStream, fieldDescriptor);
          }
          break;
        case WireFormat.WIRETYPE_START_GROUP:
        case WireFormat.WIRETYPE_END_GROUP:
          throw new UnsupportedOperationException("Groups are not supported");
        default:
          throw new IllegalArgumentException("Unexpected wire type: " + tagWireType);
      }

      switch (fieldDescriptor.getProtoFieldType()) {
        case UINT32:
          payload = UnsignedLong.valueOf((int) payload);
          break;
        case UINT64:
          payload = UnsignedLong.valueOf((long) payload);
          break;
        default:
          break;
      }

      fieldValues.put(fieldDescriptor.getFieldName(), payload);
    }

    // Protobuf encoding follows a "last one wins" semantics. This means for duplicated fields,
    // we accept the last value encountered.
    return fieldValues.buildKeepingLast();
  }

  ImmutableMap<String, Object> readAllFields(MessageLite msg, String protoTypeName)
      throws IOException {
    return readAllFields(msg.toByteArray(), protoTypeName);
  }

  Object getDefaultValue(String protoTypeName, String fieldName) {
    MessageLiteDescriptor messageDescriptor = descriptorPool.getDescriptorOrThrow(protoTypeName);
    FieldLiteDescriptor fieldDescriptor = messageDescriptor.getByFieldNameOrThrow(fieldName);

    return getDefaultValue(fieldDescriptor);
  }

  @Override
  public CelValue fromProtoMessageToCelValue(String protoTypeName, MessageLite msg) {
    checkNotNull(msg);
    checkNotNull(protoTypeName);

    MessageLiteDescriptor messageInfo =
        descriptorPool
            .findDescriptor(protoTypeName)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Could not find message info for : " + protoTypeName));
    WellKnownProto wellKnownProto =
        WellKnownProto.getByTypeName(messageInfo.getProtoTypeName()).orElse(null);

    if (wellKnownProto == null) {
      return ProtoMessageLiteValue.create(
          msg, messageInfo.getProtoTypeName(), descriptorPool, this);
    }

    switch (wellKnownProto) {
      case ANY_VALUE:
        return unpackAnyMessage((Any) msg);
      default:
        return super.fromWellKnownProtoToCelValue(msg, wellKnownProto);
    }
  }

  private CelValue unpackAnyMessage(Any anyMsg) {
    throw new UnsupportedOperationException("Unsupported");
    // String typeUrl =
    //     getTypeNameFromTypeUrl(anyMsg.getTypeUrl())
    //         .orElseThrow(
    //             () ->
    //                 new IllegalArgumentException(
    //                     String.format("malformed type URL: %s", anyMsg.getTypeUrl())));
    // MessageLiteDescriptor messageInfo =
    //     descriptorPool
    //         .findDescriptorByTypeName(typeUrl)
    //         .orElseThrow(
    //             () ->
    //                 new NoSuchElementException(
    //                     "Could not find message info for any packed message's type name: "
    //                         + anyMsg));
    //
    // Method method =
    //     ReflectionUtil.getMethod(
    //         messageInfo.getFullyQualifiedProtoJavaClassName(), "parseFrom", ByteString.class);
    // ByteString packedBytes = anyMsg.getValue();
    // MessageLite unpackedMsg = (MessageLite) ReflectionUtil.invoke(method, null, packedBytes);
    //
    // return fromProtoMessageToCelValue(unpackedMsg);
  }

  private static Optional<String> getTypeNameFromTypeUrl(String typeUrl) {
    int pos = typeUrl.lastIndexOf('/');
    if (pos != -1) {
      return Optional.of(typeUrl.substring(pos + 1));
    }
    return Optional.empty();
  }

  private ProtoLiteCelValueConverter(CelLiteDescriptorPool celLiteDescriptorPool) {
    this.descriptorPool = celLiteDescriptorPool;
  }
}
