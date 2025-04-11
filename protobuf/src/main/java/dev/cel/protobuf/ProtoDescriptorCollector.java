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

package dev.cel.protobuf;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.CelFieldValueType;
import dev.cel.protobuf.LiteDescriptorCodegenMetadata.FieldLiteDescriptorMetadata;
import java.util.ArrayDeque;
import java.util.stream.Collectors;

/**
 * ProtoDescriptorCollector inspects a {@link FileDescriptor} to collect message information into
 * {@link LiteDescriptorCodegenMetadata}. This is later utilized to create an instance of {@code MessageLiteDescriptor}.
 */
final class ProtoDescriptorCollector {

  private final DebugPrinter debugPrinter;

  ImmutableList<LiteDescriptorCodegenMetadata> collectCodegenMetadata(FileDescriptor targetFileDescriptor) {
    ImmutableList.Builder<LiteDescriptorCodegenMetadata> descriptorListBuilder = ImmutableList.builder();
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            ImmutableList.of(targetFileDescriptor), /* resolveTypeDependencies= */ false);
    ArrayDeque<Descriptor> descriptorQueue =
        celDescriptors.messageTypeDescriptors().stream()
            // Don't collect WKTs. They are included in the default descriptor pool.
            .filter(d -> !WellKnownProto.getByTypeName(d.getFullName()).isPresent())
            .collect(Collectors.toCollection(ArrayDeque::new));

    while (!descriptorQueue.isEmpty()) {
      Descriptor descriptor = descriptorQueue.pop();
      LiteDescriptorCodegenMetadata.Builder descriptorCodegenBuilder = LiteDescriptorCodegenMetadata.newBuilder();
      for (Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
        FieldLiteDescriptorMetadata.Builder fieldDescriptorCodegenBuilder = FieldLiteDescriptorMetadata.newBuilder()
            .setFieldNumber(fieldDescriptor.getNumber())
            .setFieldName(fieldDescriptor.getName())
            .setIsPacked(fieldDescriptor.isPacked())
            .setJavaType(adaptJavaType(fieldDescriptor.getJavaType()))
            .setProtoFieldType(adaptProtoType(fieldDescriptor.getType()))
            .setHasPresence(fieldDescriptor.hasPresence());

        switch (fieldDescriptor.getJavaType()) {
          case ENUM:
            fieldDescriptorCodegenBuilder.setFieldProtoTypeName(fieldDescriptor.getEnumType().getFullName());
            break;
          case MESSAGE:
            fieldDescriptorCodegenBuilder.setFieldProtoTypeName(fieldDescriptor.getMessageType().getFullName());
            break;
          default:
            break;
        }

        if (fieldDescriptor.isMapField()) {
          fieldDescriptorCodegenBuilder.setCelFieldValueType(CelFieldValueType.MAP);
          // Maps are treated as messages in proto.
          descriptorQueue.push(fieldDescriptor.getMessageType());
        } else if (fieldDescriptor.isRepeated()) {
          fieldDescriptorCodegenBuilder.setCelFieldValueType(CelFieldValueType.LIST);
        } else {
          fieldDescriptorCodegenBuilder.setCelFieldValueType(CelFieldValueType.SCALAR);
        }

        descriptorCodegenBuilder.addFieldDescriptor(fieldDescriptorCodegenBuilder.build());

        debugPrinter.print(
            String.format(
                "Collecting message %s, for field %s, type: %s",
                descriptor.getFullName(), fieldDescriptor.getFullName(), fieldDescriptor.getType()));
      }

      descriptorCodegenBuilder.setProtoTypeName(descriptor.getFullName());
      if (!descriptor.getOptions().getMapEntry()) {
        String sanitizedJavaClassName = ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(descriptor).replaceAll("\\$", ".");
        descriptorCodegenBuilder.setJavaClassName(sanitizedJavaClassName);
      }

      descriptorListBuilder.add(descriptorCodegenBuilder.build());
    }

    return descriptorListBuilder.build();
  }

  @VisibleForTesting
  static ProtoDescriptorCollector newInstance() {
    return new ProtoDescriptorCollector(DebugPrinter.newInstance(false));
  }

  static ProtoDescriptorCollector newInstance(DebugPrinter debugPrinter) {
    return new ProtoDescriptorCollector(debugPrinter);
  }

  private static FieldLiteDescriptor.Type adaptProtoType(Type type) {
    switch (type) {
      case DOUBLE:
        return FieldLiteDescriptor.Type.DOUBLE;
      case FLOAT:
        return FieldLiteDescriptor.Type.FLOAT;
      case INT64:
        return FieldLiteDescriptor.Type.INT64;
      case UINT64:
        return FieldLiteDescriptor.Type.UINT64;
      case INT32:
        return FieldLiteDescriptor.Type.INT32;
      case FIXED64:
        return FieldLiteDescriptor.Type.FIXED64;
      case FIXED32:
        return FieldLiteDescriptor.Type.FIXED32;
      case BOOL:
        return FieldLiteDescriptor.Type.BOOL;
      case STRING:
        return FieldLiteDescriptor.Type.STRING;
      case GROUP:
        return FieldLiteDescriptor.Type.GROUP;
      case MESSAGE:
        return FieldLiteDescriptor.Type.MESSAGE;
      case BYTES:
        return FieldLiteDescriptor.Type.BYTES;
      case UINT32:
        return FieldLiteDescriptor.Type.UINT32;
      case ENUM:
        return FieldLiteDescriptor.Type.ENUM;
      case SFIXED32:
        return FieldLiteDescriptor.Type.SFIXED32;
      case SFIXED64:
        return FieldLiteDescriptor.Type.SFIXED64;
      case SINT32:
        return FieldLiteDescriptor.Type.SINT32;
      case SINT64:
        return FieldLiteDescriptor.Type.SINT64;
      default:
        throw new IllegalArgumentException("Unknown Type: " + type);
    }
  }

  private static FieldLiteDescriptor.JavaType adaptJavaType(JavaType javaType) {
    switch (javaType) {
      case INT:
        return FieldLiteDescriptor.JavaType.INT;
      case LONG:
        return FieldLiteDescriptor.JavaType.LONG;
      case FLOAT:
        return FieldLiteDescriptor.JavaType.FLOAT;
      case DOUBLE:
        return FieldLiteDescriptor.JavaType.DOUBLE;
      case BOOLEAN:
        return FieldLiteDescriptor.JavaType.BOOLEAN;
      case STRING:
        return FieldLiteDescriptor.JavaType.STRING;
      case BYTE_STRING:
        return FieldLiteDescriptor.JavaType.BYTE_STRING;
      case ENUM:
        return FieldLiteDescriptor.JavaType.ENUM;
      case MESSAGE:
        return FieldLiteDescriptor.JavaType.MESSAGE;
      default:
        throw new IllegalArgumentException("Unknown JavaType: " + javaType);
    }
  }

  private ProtoDescriptorCollector(DebugPrinter debugPrinter) {
    this.debugPrinter = debugPrinter;
  }
}
