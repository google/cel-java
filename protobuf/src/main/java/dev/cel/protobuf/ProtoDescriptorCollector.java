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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.FieldDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldDescriptor.CelFieldValueType;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;

/**
 * ProtoDescriptorCollector inspects a {@link FileDescriptor} to collect message information into
 * {@link MessageLiteDescriptor}.
 */
final class ProtoDescriptorCollector {

  private final DebugPrinter debugPrinter;

  ImmutableList<MessageLiteDescriptor> collectMessageInfo(FileDescriptor targetFileDescriptor) {
    ImmutableList.Builder<MessageLiteDescriptor> messageInfoListBuilder = ImmutableList.builder();
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            ImmutableList.of(targetFileDescriptor), /* resolveTypeDependencies= */ false);
    ImmutableSet<Descriptor> messageTypes =
        celDescriptors.messageTypeDescriptors().stream()
            .filter(d -> WellKnownProto.getByTypeName(d.getFullName()) == null)
            .collect(toImmutableSet());

    for (Descriptor descriptor : messageTypes) {
      ImmutableMap.Builder<String, FieldDescriptor> fieldMap = ImmutableMap.builder();
      for (Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
        String methodSuffixName =
            CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fieldDescriptor.getName());

        String javaType = fieldDescriptor.getJavaType().toString();
        String embeddedFieldJavaClassName = "";
        String embeddedFieldProtoTypeName = "";
        switch (javaType) {
          case "ENUM":
            embeddedFieldJavaClassName =
                ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(
                    fieldDescriptor.getEnumType());
            embeddedFieldProtoTypeName = fieldDescriptor.getEnumType().getFullName();
            break;
          case "MESSAGE":
            embeddedFieldJavaClassName =
                ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(
                    fieldDescriptor.getMessageType());
            embeddedFieldProtoTypeName = fieldDescriptor.getMessageType().getFullName();
            break;
          default:
            break;
        }

        CelFieldValueType fieldValueType;
        if (fieldDescriptor.isMapField()) {
          fieldValueType = CelFieldValueType.MAP;
        } else if (fieldDescriptor.isRepeated()) {
          fieldValueType = CelFieldValueType.LIST;
        } else {
          fieldValueType = CelFieldValueType.SCALAR;
        }

        fieldMap.put(
            fieldDescriptor.getName(),
            new FieldDescriptor(
                /* fullyQualifiedProtoTypeName= */ fieldDescriptor.getFullName(),
                /* javaTypeName= */ javaType,
                /* methodSuffixName= */ methodSuffixName,
                /* celFieldValueType= */ fieldValueType.toString(),
                /* protoFieldType= */ fieldDescriptor.getType().toString(),
                /* hasHasser= */ String.valueOf(fieldDescriptor.hasPresence()),
                /* fieldJavaClassName= */ embeddedFieldJavaClassName,
                /* fieldProtoTypeName= */ embeddedFieldProtoTypeName));

        debugPrinter.print(
            String.format(
                "Method suffix name in %s, for field %s: %s",
                descriptor.getFullName(), fieldDescriptor.getFullName(), methodSuffixName));
        debugPrinter.print(String.format("FieldType: %s", fieldValueType));
        if (!embeddedFieldJavaClassName.isEmpty()) {
          debugPrinter.print(
              String.format(
                  "Java class name for field %s: %s",
                  fieldDescriptor.getName(), embeddedFieldJavaClassName));
        }
      }


      messageInfoListBuilder.add(
          new MessageLiteDescriptor(
              descriptor.getFullName(),
              // TODO: Message class instead
              descriptor.getClass(),
              // ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(descriptor),
              fieldMap.buildOrThrow()));
    }

    return messageInfoListBuilder.build();
  }

  static ProtoDescriptorCollector newInstance(DebugPrinter debugPrinter) {
    return new ProtoDescriptorCollector(debugPrinter);
  }

  private ProtoDescriptorCollector(DebugPrinter debugPrinter) {
    this.debugPrinter = debugPrinter;
  }
}
