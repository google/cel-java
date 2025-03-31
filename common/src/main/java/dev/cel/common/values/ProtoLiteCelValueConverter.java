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

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.MessageLite;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.ReflectionUtil;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.FieldDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.lang.reflect.Method;
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

  /** Adapts the protobuf message field into {@link CelValue}. */
  public CelValue fromProtoMessageFieldToCelValue(MessageLite msg, FieldDescriptor fieldInfo) {
    checkNotNull(msg);
    checkNotNull(fieldInfo);

    Method getterMethod = ReflectionUtil.getMethod(msg.getClass(), fieldInfo.getGetterName());
    Object fieldValue = ReflectionUtil.invoke(getterMethod, msg);

    switch (fieldInfo.getProtoFieldType()) {
      case UINT32:
        fieldValue = UnsignedLong.valueOf((int) fieldValue);
        break;
      case UINT64:
        fieldValue = UnsignedLong.valueOf((long) fieldValue);
        break;
      default:
        break;
    }

    return fromJavaObjectToCelValue(fieldValue);
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
        WellKnownProto.getByTypeName(messageInfo.getProtoTypeName());

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
