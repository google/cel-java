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

package dev.cel.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageOrBuilder;
import dev.cel.common.types.CelType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import java.util.Optional;

/**
 * {@code DescriptorTypeResolver} extends {@link TypeResolver} and additionally resolves incoming
 * protobuf message types using descriptors.
 */
@Immutable
final class DescriptorTypeResolver extends TypeResolver {

  static DescriptorTypeResolver create() {
    return new DescriptorTypeResolver();
  }

  @Override
  TypeType resolveObjectType(Object obj, CelType typeCheckedType) {
    checkNotNull(obj);

    Optional<TypeType> wellKnownTypeType = resolveWellKnownObjectType(obj);
    if (wellKnownTypeType.isPresent()) {
      return wellKnownTypeType.get();
    }

    if (obj instanceof MessageOrBuilder) {
      MessageOrBuilder msg = (MessageOrBuilder) obj;
      return TypeType.create(StructTypeReference.create(msg.getDescriptorForType().getFullName()));
    }

    return super.resolveObjectType(obj, typeCheckedType);
  }

  private DescriptorTypeResolver() {}
}
