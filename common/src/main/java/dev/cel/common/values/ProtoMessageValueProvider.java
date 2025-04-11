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

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoAdapter;
import dev.cel.common.internal.ProtoMessageFactory;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ProtoMessageValueProvider} constructs new instances of a protobuf message given its fully
 * qualified name and its fields to populate.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public class ProtoMessageValueProvider implements CelValueProvider {
  private final ProtoAdapter protoAdapter;
  private final ProtoMessageFactory protoMessageFactory;
  private final ProtoCelValueConverter protoCelValueConverter;

  @Override
  public Optional<CelValue> newValue(String structType, Map<String, Object> fields) {
    try {
      Message.Builder builder = getMessageBuilderOrThrow(structType);
      Descriptor descriptor = builder.getDescriptorForType();
      for (Map.Entry<String, Object> entry : fields.entrySet()) {
        FieldDescriptor fieldDescriptor = findField(descriptor, entry.getKey());
        Optional<Object> fieldValue =
            protoAdapter.adaptValueToFieldType(
                fieldDescriptor, entry.getValue()); // TODO: Decouple ProtoAdapter
        fieldValue.ifPresent(o -> builder.setField(fieldDescriptor, o));
      }

      return Optional.of(protoCelValueConverter.fromProtoMessageToCelValue(builder.build()));
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  private Message.Builder getMessageBuilderOrThrow(String messageName) {
    return protoMessageFactory
        .newBuilder(messageName)
        .orElseThrow(
            () ->
                new CelRuntimeException(
                    new IllegalArgumentException(
                        String.format("cannot resolve '%s' as a message", messageName)),
                    CelErrorCode.ATTRIBUTE_NOT_FOUND));
  }

  private FieldDescriptor findField(Descriptor descriptor, String fieldName) {
    FieldDescriptor fieldDescriptor = descriptor.findFieldByName(fieldName);
    if (fieldDescriptor != null) {
      return fieldDescriptor;
    }

    return protoMessageFactory
        .getDescriptorPool()
        .findExtensionDescriptor(descriptor, fieldName)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(
                        "field '%s' is not declared in message '%s'",
                        fieldName, descriptor.getFullName())));
  }

  public static ProtoMessageValueProvider newInstance(
      DynamicProto dynamicProto) {
    return new ProtoMessageValueProvider(dynamicProto);
  }

  private ProtoMessageValueProvider(DynamicProto dynamicProto) {
    this.protoMessageFactory = dynamicProto.getProtoMessageFactory();
    this.protoCelValueConverter =
        ProtoCelValueConverter.newInstance(
            protoMessageFactory.getDescriptorPool(), dynamicProto);
    this.protoAdapter = new ProtoAdapter(dynamicProto, true);
  }
}
