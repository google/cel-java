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
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.types.CelType;
import dev.cel.common.types.StructTypeReference;
import java.util.Optional;

/** ProtoMessageValue is a struct value with protobuf support. */
@AutoValue
@Immutable
public abstract class ProtoMessageValue extends StructValue<StringValue> {

  @Override
  public abstract Message value();

  @Override
  public abstract CelType celType();

  abstract CelDescriptorPool celDescriptorPool();

  abstract ProtoCelValueConverter protoCelValueConverter();

  @Override
  public boolean isZeroValue() {
    return value().getDefaultInstanceForType().equals(value());
  }

  @Override
  public CelValue select(StringValue field) {
    FieldDescriptor fieldDescriptor =
        findField(celDescriptorPool(), value().getDescriptorForType(), field.value());

    return protoCelValueConverter().fromProtoMessageFieldToCelValue(value(), fieldDescriptor);
  }

  @Override
  public Optional<CelValue> find(StringValue field) {
    FieldDescriptor fieldDescriptor =
        findField(celDescriptorPool(), value().getDescriptorForType(), field.value());

    // Selecting a field on a protobuf message yields a default value even if the field is not
    // declared. Therefore, we must exhaustively test whether they are actually declared.
    if (fieldDescriptor.isRepeated()) {
      if (value().getRepeatedFieldCount(fieldDescriptor) == 0) {
        return Optional.empty();
      }
    } else if (!value().hasField(fieldDescriptor)) {
      return Optional.empty();
    }

    return Optional.of(
        protoCelValueConverter().fromProtoMessageFieldToCelValue(value(), fieldDescriptor));
  }

  public static ProtoMessageValue create(
      Message value,
      CelDescriptorPool celDescriptorPool,
      ProtoCelValueConverter protoCelValueConverter) {
    Preconditions.checkNotNull(value);
    Preconditions.checkNotNull(celDescriptorPool);
    Preconditions.checkNotNull(protoCelValueConverter);
    return new AutoValue_ProtoMessageValue(
        value,
        StructTypeReference.create(value.getDescriptorForType().getFullName()),
        celDescriptorPool,
        protoCelValueConverter);
  }

  private FieldDescriptor findField(
      CelDescriptorPool celDescriptorPool, Descriptor descriptor, String fieldName) {
    FieldDescriptor fieldDescriptor = descriptor.findFieldByName(fieldName);
    if (fieldDescriptor != null) {
      return fieldDescriptor;
    }

    return celDescriptorPool
        .findExtensionDescriptor(descriptor, fieldName)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(
                        "field '%s' is not declared in message '%s'",
                        fieldName, descriptor.getFullName())));
  }
}
