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

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.types.StructTypeReference;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.io.IOException;
import java.util.Optional;

/** ProtoMessageLiteValue is a struct value with protobuf support. */
@AutoValue
@Immutable
public abstract class ProtoMessageLiteValue extends StructValue<StringValue> {

  @Override
  public abstract MessageLite value();

  @Override
  public abstract StructTypeReference celType();

  abstract CelLiteDescriptorPool descriptorPool();

  abstract ProtoLiteCelValueConverter protoLiteCelValueConverter();

  @Memoized
  ImmutableMap<String, Object> fieldValues() {
    ImmutableMap<String, Object> allFieldValues;
    try {
      allFieldValues = protoLiteCelValueConverter().readAllFields(value(), celType().name());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return allFieldValues;
  }

  @Override
  public boolean isZeroValue() {
    return value().getDefaultInstanceForType().equals(value());
  }

  @Override
  public CelValue select(StringValue field) {
    Object fieldValue = fieldValues().getOrDefault(
          field.value(),
          protoLiteCelValueConverter().getDefaultValue(celType().name(), field.value()));
    return protoLiteCelValueConverter().fromJavaObjectToCelValue(fieldValue);
  }

  @Override
  public Optional<CelValue> find(StringValue field) {
    MessageLiteDescriptor messageInfo = descriptorPool().getDescriptorOrThrow(celType().name());
    FieldLiteDescriptor fieldInfo = messageInfo.getFieldDescriptorsMap().get(field.value());

    CelValue selectedValue = select(field);
    if (fieldInfo.getHasHasser()) {
      if (selectedValue.equals(NullValue.NULL_VALUE)) {
        return Optional.empty();
      }
    } else if (selectedValue.isZeroValue()){
      return Optional.empty();
    }

    return Optional.of(selectedValue);
  }

  public static ProtoMessageLiteValue create(
      MessageLite value,
      String protoTypeName,
      CelLiteDescriptorPool descriptorPool,
      ProtoLiteCelValueConverter protoLiteCelValueConverter) {
    Preconditions.checkNotNull(value);
    return new AutoValue_ProtoMessageLiteValue(
        value, StructTypeReference.create(protoTypeName), descriptorPool, protoLiteCelValueConverter);
  }
}
