// Copyright 2022 Google LLC
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

package dev.cel.common.types;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.FileDescriptorSetConverter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The {@code ProtoMessageTypeProvider} implements the {@link CelTypeProvider} interface to provide
 * {@code CelType} objects for the core CEL types and a list of protobuf message types.
 */
@CheckReturnValue
@Immutable
public final class ProtoMessageTypeProvider implements CelTypeProvider {

  private static final ImmutableMap<FieldDescriptor.Type, CelType> PROTO_TYPE_TO_CEL_TYPE =
      ImmutableMap.<FieldDescriptor.Type, CelType>builder()
          .put(FieldDescriptor.Type.BOOL, SimpleType.BOOL)
          .put(FieldDescriptor.Type.BYTES, SimpleType.BYTES)
          .put(FieldDescriptor.Type.DOUBLE, SimpleType.DOUBLE)
          .put(FieldDescriptor.Type.FLOAT, SimpleType.DOUBLE)
          .put(FieldDescriptor.Type.FIXED32, SimpleType.UINT)
          .put(FieldDescriptor.Type.FIXED64, SimpleType.UINT)
          .put(FieldDescriptor.Type.INT32, SimpleType.INT)
          .put(FieldDescriptor.Type.INT64, SimpleType.INT)
          .put(FieldDescriptor.Type.SFIXED32, SimpleType.INT)
          .put(FieldDescriptor.Type.SFIXED64, SimpleType.INT)
          .put(FieldDescriptor.Type.SINT32, SimpleType.INT)
          .put(FieldDescriptor.Type.SINT64, SimpleType.INT)
          .put(FieldDescriptor.Type.STRING, SimpleType.STRING)
          .put(FieldDescriptor.Type.UINT32, SimpleType.UINT)
          .put(FieldDescriptor.Type.UINT64, SimpleType.UINT)
          .buildOrThrow();

  private final ImmutableMap<String, CelType> allTypes;

  public ProtoMessageTypeProvider() {
    this(CelDescriptors.builder().build());
  }

  public ProtoMessageTypeProvider(FileDescriptorSet descriptorSet) {
    this(
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            FileDescriptorSetConverter.convert(descriptorSet)));
  }

  public ProtoMessageTypeProvider(Iterable<Descriptor> descriptors) {
    this(
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            ImmutableSet.copyOf(Iterables.transform(descriptors, Descriptor::getFile))));
  }

  public ProtoMessageTypeProvider(ImmutableSet<FileDescriptor> fileDescriptors) {
    this(CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptors));
  }

  public ProtoMessageTypeProvider(CelDescriptors celDescriptors) {
    this.allTypes =
        ImmutableMap.<String, CelType>builder()
            .putAll(createEnumTypes(celDescriptors.enumDescriptors()))
            .putAll(
                createProtoMessageTypes(
                    celDescriptors.messageTypeDescriptors(), celDescriptors.extensionDescriptors()))
            .buildOrThrow();
  }

  @Override
  public ImmutableCollection<CelType> types() {
    return allTypes.values();
  }

  @Override
  public Optional<CelType> findType(String typeName) {
    return Optional.ofNullable(allTypes.get(typeName));
  }

  // This method reference implements @Immutable interface FieldResolver, but the declaration of
  // type 'com.google.api.expr.cel.ProtoMessageTypeProvider.FieldResolver' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  private ImmutableMap<String, CelType> createProtoMessageTypes(
      Collection<Descriptor> descriptors, ImmutableMultimap<String, FieldDescriptor> extensionMap) {
    Map<String, CelType> protoMessageTypes = new HashMap<>();
    for (Descriptor descriptor : descriptors) {
      if (protoMessageTypes.containsKey(descriptor.getFullName())) {
        continue;
      }
      ImmutableList<String> fieldNames =
          descriptor.getFields().stream().map(FieldDescriptor::getName).collect(toImmutableList());

      Map<String, FieldDescriptor> extensionFields = new HashMap<>();
      for (FieldDescriptor extension : extensionMap.get(descriptor.getFullName())) {
        extensionFields.putIfAbsent(extension.getFullName(), extension);
      }
      ImmutableMap<String, FieldDescriptor> extensions = ImmutableMap.copyOf(extensionFields);

      protoMessageTypes.put(
          descriptor.getFullName(),
          ProtoMessageType.create(
              descriptor.getFullName(),
              ImmutableSet.copyOf(fieldNames),
              new FieldResolver(this, descriptor)::findField,
              new FieldResolver(this, extensions)::findField));
    }
    return ImmutableMap.copyOf(protoMessageTypes);
  }

  private ImmutableMap<String, CelType> createEnumTypes(
      Collection<EnumDescriptor> enumDescriptors) {
    HashMap<String, CelType> enumTypes = new HashMap<>();
    for (EnumDescriptor enumDescriptor : enumDescriptors) {
      if (enumTypes.containsKey(enumDescriptor.getFullName())) {
        continue;
      }
      ImmutableMap<String, Integer> values =
          enumDescriptor.getValues().stream()
              .collect(
                  toImmutableMap(EnumValueDescriptor::getName, EnumValueDescriptor::getNumber));
      enumTypes.put(
          enumDescriptor.getFullName(), EnumType.create(enumDescriptor.getFullName(), values));
    }
    return ImmutableMap.copyOf(enumTypes);
  }

  private static class FieldResolver {
    private final CelTypeProvider celTypeProvider;
    private final ImmutableMap<String, FieldDescriptor> fields;

    private FieldResolver(CelTypeProvider celTypeProvider, Descriptor descriptor) {
      this(
          celTypeProvider,
          descriptor.getFields().stream()
              .collect(toImmutableMap(FieldDescriptor::getName, Function.identity())));
    }

    private FieldResolver(
        CelTypeProvider celTypeProvider, ImmutableMap<String, FieldDescriptor> fields) {
      this.celTypeProvider = celTypeProvider;
      this.fields = fields;
    }

    private Optional<CelType> findField(String fieldName) {
      FieldDescriptor fieldDescriptor = fields.get(fieldName);
      if (fieldDescriptor == null) {
        return Optional.empty();
      }
      return findFieldInternal(fieldDescriptor);
    }

    private Optional<CelType> findFieldInternal(FieldDescriptor fieldDescriptor) {
      boolean isRepeated = fieldDescriptor.isRepeated();
      CelType fieldType;
      switch (fieldDescriptor.getType()) {
        case GROUP:
        case MESSAGE:
          Descriptor descriptor = fieldDescriptor.getMessageType();
          if (fieldDescriptor.isMapField()) {
            // The key and value types are always guaranteed to be at index 0 and 1 respectively.
            FieldDescriptor keyField = descriptor.getFields().get(0);
            FieldDescriptor valueField = descriptor.getFields().get(1);
            Optional<CelType> key = findFieldInternal(keyField);
            Optional<CelType> value = findFieldInternal(valueField);
            if (key.isPresent() && value.isPresent()) {
              return Optional.of(MapType.create(key.get(), value.get()));
            }
            return Optional.empty();
          }
          String messageName = descriptor.getFullName();
          fieldType =
              CelTypes.getWellKnownCelType(messageName)
                  .orElse(celTypeProvider.findType(descriptor.getFullName()).orElse(null));
          break;
        case ENUM:
          EnumDescriptor enumDescriptor = fieldDescriptor.getEnumType();
          fieldType = celTypeProvider.findType(enumDescriptor.getFullName()).orElse(null);
          break;
        default:
          fieldType = PROTO_TYPE_TO_CEL_TYPE.get(fieldDescriptor.getType());
          break;
      }
      if (fieldType == null) {
        return Optional.empty();
      }
      if (isRepeated) {
        return Optional.of(ListType.create(fieldType));
      }
      return Optional.of(fieldType);
    }
  }
}
