// Copyright 2024 Google LLC
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code CelTypeResolver} resolves incoming {@link CelType} into {@link TypeType}., either as part
 * of a type call (type('foo'), type(1), etc.) or as a type literal (type, int, string, etc.)
 */
@Immutable
final class CelTypeResolver {

  // Sentinel runtime value representing the special "type" ident. This ensures following to be
  // true: type == type(string) && type == type(type("foo"))
  private static final TypeType RUNTIME_TYPE_TYPE = TypeType.create(SimpleType.DYN);

  private static final ImmutableMap<Class<?>, TypeType> COMMON_TYPES =
      ImmutableMap.<Class<?>, TypeType>builder()
          .put(Boolean.class, TypeType.create(SimpleType.BOOL))
          .put(Double.class, TypeType.create(SimpleType.DOUBLE))
          .put(Long.class, TypeType.create(SimpleType.INT))
          .put(UnsignedLong.class, TypeType.create(SimpleType.UINT))
          .put(String.class, TypeType.create(SimpleType.STRING))
          .put(NullValue.class, TypeType.create(SimpleType.NULL_TYPE))
          .put(Duration.class, TypeType.create(SimpleType.DURATION))
          .put(Timestamp.class, TypeType.create(SimpleType.TIMESTAMP))
          .put(ArrayList.class, TypeType.create(ListType.create(SimpleType.DYN)))
          .put(ImmutableList.class, TypeType.create(ListType.create(SimpleType.DYN)))
          .put(HashMap.class, TypeType.create(MapType.create(SimpleType.DYN, SimpleType.DYN)))
          .put(ImmutableMap.class, TypeType.create(MapType.create(SimpleType.DYN, SimpleType.DYN)))
          .put(Optional.class, TypeType.create(OptionalType.create(SimpleType.DYN)))
          .buildOrThrow();

  private static final ImmutableMap<Class<?>, TypeType> EXTENDABLE_TYPES =
      ImmutableMap.<Class<?>, TypeType>builder()
          .put(Collection.class, TypeType.create(ListType.create(SimpleType.DYN)))
          .put(ByteString.class, TypeType.create(SimpleType.BYTES))
          .put(Map.class, TypeType.create(MapType.create(SimpleType.DYN, SimpleType.DYN)))
          .buildOrThrow();

  /** Adapt the type-checked {@link CelType} into a runtime type value {@link TypeType}. */
  static TypeType adaptType(CelType typeCheckedType) {
    checkNotNull(typeCheckedType);

    switch (typeCheckedType.kind()) {
      case TYPE:
        CelType typeOfType = ((TypeType) typeCheckedType).type();
        switch (typeOfType.kind()) {
          case STRUCT:
            return TypeType.create(adaptStructType((StructType) typeOfType));
          default:
            return (TypeType) typeCheckedType;
        }
      case UNSPECIFIED:
        throw new IllegalArgumentException("Unsupported CelType kind: " + typeCheckedType.kind());
      default:
        return TypeType.create(typeCheckedType);
    }
  }

  /** Resolve the CEL type of the {@code obj}. */
  static TypeType resolveObjectType(Object obj, CelType typeCheckedType) {
    checkNotNull(obj);
    if (obj instanceof TypeType) {
      return RUNTIME_TYPE_TYPE;
    }

    Class<?> currentClass = obj.getClass();
    TypeType runtimeType = COMMON_TYPES.get(currentClass);
    if (runtimeType != null) {
      return runtimeType;
    }

    if (obj instanceof MessageOrBuilder) {
      MessageOrBuilder msg = (MessageOrBuilder) obj;
      // TODO: Replace with CelLiteDescriptor
      return TypeType.create(StructTypeReference.create(msg.getDescriptorForType().getFullName()));
    }

    // Handle types that the client may have extended.
    while (currentClass != null) {
      runtimeType = EXTENDABLE_TYPES.get(currentClass);
      if (runtimeType != null) {
        return runtimeType;
      }

      // Check interfaces
      for (Class<?> interfaceClass : currentClass.getInterfaces()) {
        runtimeType = EXTENDABLE_TYPES.get(interfaceClass);
        if (runtimeType != null) {
          return runtimeType;
        }
      }
      currentClass = currentClass.getSuperclass();
    }

    // This is an opaque type, or something CEL doesn't know about.
    return (TypeType) typeCheckedType;
  }

  private static CelType adaptStructType(StructType typeOfType) {
    String structName = typeOfType.name();
    CelType newTypeOfType;
    if (structName.equals(SimpleType.DURATION.name())) {
      newTypeOfType = SimpleType.DURATION;
    } else if (structName.equals(SimpleType.TIMESTAMP.name())) {
      newTypeOfType = SimpleType.TIMESTAMP;
    } else {
      // Coerces ProtoMessageTypeProvider to be a struct type reference for accurate
      // equality tests.
      // In the future, we can plumb ProtoMessageTypeProvider through the runtime to retain
      // ProtoMessageType here.
      newTypeOfType = StructTypeReference.create(typeOfType.name());
    }
    return newTypeOfType;
  }

  private CelTypeResolver() {}
}
