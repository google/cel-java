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

package dev.cel.common.types;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** TODO: Add */
public final class ProtoMessageLiteTypeProvider implements CelTypeProvider {
  private static final ImmutableMap<FieldLiteDescriptor.Type, CelType> PROTO_TYPE_TO_CEL_TYPE =
      ImmutableMap.<FieldLiteDescriptor.Type, CelType>builder()
          .put(FieldLiteDescriptor.Type.DOUBLE, SimpleType.DOUBLE)
          .put(FieldLiteDescriptor.Type.FLOAT, SimpleType.DOUBLE)
          .put(FieldLiteDescriptor.Type.INT64, SimpleType.INT)
          .put(FieldLiteDescriptor.Type.INT32, SimpleType.INT)
          .put(FieldLiteDescriptor.Type.SFIXED32, SimpleType.INT)
          .put(FieldLiteDescriptor.Type.SFIXED64, SimpleType.INT)
          .put(FieldLiteDescriptor.Type.SINT32, SimpleType.INT)
          .put(FieldLiteDescriptor.Type.SINT64, SimpleType.INT)
          .put(FieldLiteDescriptor.Type.BOOL, SimpleType.BOOL)
          .put(FieldLiteDescriptor.Type.STRING, SimpleType.STRING)
          .put(FieldLiteDescriptor.Type.BYTES, SimpleType.BYTES)
          .put(FieldLiteDescriptor.Type.FIXED32, SimpleType.UINT)
          .put(FieldLiteDescriptor.Type.FIXED64, SimpleType.UINT)
          .put(FieldLiteDescriptor.Type.UINT32, SimpleType.UINT)
          .put(FieldLiteDescriptor.Type.UINT64, SimpleType.UINT)
          .buildOrThrow();

  private final ImmutableMap<String, CelType> allTypes;

  @Override
  public ImmutableCollection<CelType> types() {
    return allTypes.values();
  }

  @Override
  public Optional<CelType> findType(String typeName) {
    return Optional.empty();
  }

  public static ProtoMessageLiteTypeProvider newInstance(CelLiteDescriptor... celLiteDescriptors) {
    return newInstance(ImmutableSet.copyOf(celLiteDescriptors));
  }

  public static ProtoMessageLiteTypeProvider newInstance(
      Set<CelLiteDescriptor> celLiteDescriptors) {
    return new ProtoMessageLiteTypeProvider(celLiteDescriptors);
  }

  private ProtoMessageLiteTypeProvider(Set<CelLiteDescriptor> celLiteDescriptors) {
    ImmutableMap.Builder<String, CelType> builder = ImmutableMap.builder();
    for (CelLiteDescriptor descriptor : celLiteDescriptors) {
      for (Entry<String, MessageLiteDescriptor> entry :
          descriptor.getProtoTypeNamesToDescriptors().entrySet()) {
        builder.put(entry.getKey(), createMessageType(entry.getValue()));
      }
    }

    this.allTypes = builder.buildOrThrow();
  }

  private static ProtoMessageType createMessageType(MessageLiteDescriptor messageLiteDescriptor) {
    ImmutableMap<String, FieldLiteDescriptor> fields =
        messageLiteDescriptor.getFieldDescriptors().stream()
            .collect(toImmutableMap(FieldLiteDescriptor::getFieldName, Function.identity()));

    return new ProtoMessageType(
        messageLiteDescriptor.getProtoTypeName(),
        fields.keySet(),
        new FieldResolver(fields),
        extensionFieldName -> {
          throw new UnsupportedOperationException(
              "Proto extensions are not yet supported in MessageLite.");
        },
        jsonFieldName -> {
          throw new UnsupportedOperationException("JSON name is not yet supported in MessageLite.");
        });
  }

  private static class FieldResolver implements StructType.FieldResolver {
    private final ImmutableMap<String, FieldLiteDescriptor> fields;

    @Override
    public Optional<CelType> findField(String fieldName) {
      FieldLiteDescriptor fieldDescriptor = fields.get(fieldName);
      if (fieldDescriptor == null) {
        return Optional.empty();
      }

      FieldLiteDescriptor.Type fieldType = fieldDescriptor.getProtoFieldType();
      switch (fieldDescriptor.getProtoFieldType()) {
        default:
          return Optional.of(PROTO_TYPE_TO_CEL_TYPE.get(fieldType));
      }
    }

    private FieldResolver(ImmutableMap<String, FieldLiteDescriptor> fields) {
      this.fields = fields;
    }
  }
}
