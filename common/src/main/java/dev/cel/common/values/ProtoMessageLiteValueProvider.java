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
import dev.cel.common.internal.DefaultLiteDescriptorPool;
import dev.cel.protobuf.CelLiteDescriptor;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code ProtoMessageValueProvider} constructs new instances of protobuf lite-message given its
 * fully qualified name and its fields to populate.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
public class ProtoMessageLiteValueProvider implements CelValueProvider {
  private final ProtoLiteCelValueConverter protoLiteCelValueConverter;

  public ProtoLiteCelValueConverter getProtoLiteCelValueConverter() {
    return protoLiteCelValueConverter;
  }

  @Override
  public Optional<CelValue> newValue(String structType, Map<String, Object> fields) {
    throw new UnsupportedOperationException("Message creation is not supported yet.");
  }


  public static ProtoMessageLiteValueProvider newInstance(
      CelLiteDescriptor... descriptors) {
    return newInstance(ImmutableSet.copyOf(descriptors));
  }

  public static ProtoMessageLiteValueProvider newInstance(Set<CelLiteDescriptor> descriptors) {
    DefaultLiteDescriptorPool descriptorPool = DefaultLiteDescriptorPool.newInstance(ImmutableSet.copyOf(descriptors));
    ProtoLiteCelValueConverter protoLiteCelValueConverter =
        ProtoLiteCelValueConverter.newInstance(descriptorPool);
    return new ProtoMessageLiteValueProvider(protoLiteCelValueConverter);
  }

  private ProtoMessageLiteValueProvider(
      ProtoLiteCelValueConverter protoLiteCelValueConverter) {
    this.protoLiteCelValueConverter = protoLiteCelValueConverter;
  }
}
