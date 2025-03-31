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

package dev.cel.common.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Optional;

/** Descriptor pool for {@link CelLiteDescriptor}s. */
@Immutable
@Internal
public final class DefaultLiteDescriptorPool implements CelLiteDescriptorPool {
  private final ImmutableMap<String, MessageLiteDescriptor> protoFqnToMessageInfo;

  public static DefaultLiteDescriptorPool newInstance(ImmutableSet<CelLiteDescriptor> descriptors) {
    return new DefaultLiteDescriptorPool(descriptors);
  }

  @Override
  public Optional<MessageLiteDescriptor> findDescriptor(String protoTypeName) {
    return Optional.ofNullable(protoFqnToMessageInfo.get(protoTypeName));
  }

  private static MessageLiteDescriptor newMessageInfo(WellKnownProto wellKnownProto) {
    ImmutableMap.Builder<String, FieldDescriptor> fieldInfoMap = ImmutableMap.builder();
    // switch (wellKnownProto) {
    //   case JSON_STRUCT_VALUE:
    //     fieldInfoMap.put(
    //         "fields",
    //         new FieldDescriptor(
    //             "google.protobuf.Struct.fields",
    //             "MESSAGE",
    //             "Fields",
    //             FieldDescriptor.CelFieldValueType.MAP.toString(),
    //             FieldDescriptor.Type.MESSAGE.toString(),
    //             String.valueOf(false),
    //             "com.google.protobuf.Struct$FieldsEntry",
    //             "google.protobuf.Struct.FieldsEntry"));
    //     break;
    //   case BOOL_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.BoolValue",
    //             "BOOLEAN",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.BOOL));
    //     break;
    //   case BYTES_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.BytesValue",
    //             "BYTE_STRING",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.BYTES));
    //     break;
    //   case DOUBLE_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.DoubleValue",
    //             "DOUBLE",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.DOUBLE));
    //     break;
    //   case FLOAT_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.FloatValue",
    //             "FLOAT",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.FLOAT));
    //     break;
    //   case INT32_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.Int32Value",
    //             "INT",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.INT32));
    //     break;
    //   case INT64_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.Int64Value",
    //             "LONG",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.INT64));
    //     break;
    //   case STRING_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.StringValue",
    //             "STRING",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.STRING));
    //     break;
    //   case UINT32_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.UInt32Value",
    //             "INT",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.UINT32));
    //     break;
    //   case UINT64_VALUE:
    //     fieldInfoMap.put(
    //         "value",
    //         newPrimitiveFieldInfo(
    //             "google.protobuf.UInt64Value",
    //             "LONG",
    //             FieldDescriptor.CelFieldValueType.SCALAR,
    //             FieldDescriptor.Type.UINT64));
    //     break;
    //   case JSON_VALUE:
    //   case JSON_LIST_VALUE:
    //   case DURATION:
    //   case TIMESTAMP:
    //     // TODO: Complete these
    //     break;
    //   default:
    //     break;
    // }

    return new MessageLiteDescriptor(
        wellKnownProto.typeName(), fieldInfoMap.buildOrThrow());
  }

  private static FieldDescriptor newPrimitiveFieldInfo(
      String fullyQualifiedProtoName,
      String javaTypeName,
      FieldDescriptor.CelFieldValueType valueType,
      FieldDescriptor.Type protoFieldType) {
    return new FieldDescriptor(
        fullyQualifiedProtoName + ".value",
        javaTypeName,
        valueType.toString(),
        protoFieldType.toString(),
        String.valueOf(false),
        fullyQualifiedProtoName);
  }

  private DefaultLiteDescriptorPool(ImmutableSet<CelLiteDescriptor> descriptors) {
    ImmutableMap.Builder<String, MessageLiteDescriptor> protoFqnMapBuilder = ImmutableMap.builder();
    for (WellKnownProto wellKnownProto : WellKnownProto.values()) {
      MessageLiteDescriptor wktMessageInfo = newMessageInfo(wellKnownProto);
      protoFqnMapBuilder.put(wellKnownProto.typeName(), wktMessageInfo);
    }

    for (CelLiteDescriptor descriptor : descriptors) {
      protoFqnMapBuilder.putAll(descriptor.getProtoTypeNamesToDescriptors());
    }

    this.protoFqnToMessageInfo = protoFqnMapBuilder.buildOrThrow();
  }
}
