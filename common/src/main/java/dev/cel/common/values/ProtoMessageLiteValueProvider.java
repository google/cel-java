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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.annotations.Beta;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.DefaultLiteDescriptorPool;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code ProtoMessageValueProvider} constructs new instances of protobuf lite-message given its
 * fully qualified name and its fields to populate.
 */
@Immutable
@Beta
public class ProtoMessageLiteValueProvider implements CelValueProvider {
  private final CelLiteDescriptorPool descriptorPool;
  private final ProtoLiteCelValueConverter protoLiteCelValueConverter;

  @Override
  public CelValueConverter celValueConverter() {
    return protoLiteCelValueConverter;
  }

  @Override
  public Optional<Object> newValue(String structType, Map<String, Object> fields) {
    MessageLiteDescriptor descriptor = descriptorPool.findDescriptor(structType).orElse(null);
    if (descriptor == null) {
      return Optional.empty();
    }

    if (!fields.isEmpty()) {
      // TODO: Add support for this
      throw new UnsupportedOperationException(
          "Message creation with prepopulated fields is not supported yet.");
    }

    MessageLite message = descriptor.newMessageBuilder().build();
    return Optional.of(protoLiteCelValueConverter.toRuntimeValue(message));
  }

  public static ProtoMessageLiteValueProvider newInstance(CelLiteDescriptor... descriptors) {
    return newInstance(ImmutableSet.copyOf(descriptors));
  }

  public static ProtoMessageLiteValueProvider newInstance(Set<CelLiteDescriptor> descriptors) {
    DefaultLiteDescriptorPool descriptorPool =
        DefaultLiteDescriptorPool.newInstance(ImmutableSet.copyOf(descriptors));
    ProtoLiteCelValueConverter protoLiteCelValueConverter =
        ProtoLiteCelValueConverter.newInstance(descriptorPool);
    return new ProtoMessageLiteValueProvider(protoLiteCelValueConverter, descriptorPool);
  }

  private ProtoMessageLiteValueProvider(
      ProtoLiteCelValueConverter protoLiteCelValueConverter, CelLiteDescriptorPool descriptorPool) {
    this.protoLiteCelValueConverter = protoLiteCelValueConverter;
    this.descriptorPool = descriptorPool;
  }
}
