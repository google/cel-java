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

package dev.cel.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.api.expr.v1alpha1.Type.TypeKindCase;
import com.google.api.expr.v1alpha1.Value;
import com.google.api.expr.v1alpha1.Value.KindCase;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.TypeType;
import java.util.Collection;
import java.util.Map;
import org.jspecify.nullness.Nullable;

/**
 * The {@code StandardTypeResolver} implements the {@link TypeResolver} and resolves types supported
 * by the CEL standard environment.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class StandardTypeResolver implements TypeResolver {

  /**
   * Obtain a singleton instance of the {@link StandardTypeResolver} appropriate for the {@code
   * celOptions} provided.
   */
  public static TypeResolver getInstance(CelOptions celOptions) {
    return celOptions.enableUnsignedLongs() ? INSTANCE_WITH_UNSIGNED_LONGS : INSTANCE;
  }

  private static final TypeResolver INSTANCE =
      new StandardTypeResolver(commonTypes(/* unsignedLongs= */ false));

  private static final TypeResolver INSTANCE_WITH_UNSIGNED_LONGS =
      new StandardTypeResolver(commonTypes(/* unsignedLongs= */ true));

  // Type of type which is modelled as a value instance rather than as a Java POJO.
  private static final Value TYPE_VALUE = createType("type");

  // Built-in types.
  private static ImmutableMap<Value, Class<?>> commonTypes(boolean unsignedLongs) {
    return ImmutableMap.<Value, Class<?>>builder()
        .put(createType("bool"), Boolean.class)
        .put(createType("bytes"), ByteString.class)
        .put(createType("double"), Double.class)
        .put(createType("int"), Long.class)
        .put(createType("uint"), unsignedLongs ? UnsignedLong.class : Long.class)
        .put(createType("string"), String.class)
        .put(createType("null_type"), NullValue.class)
        // Aggregate types.
        .put(createType("list"), Collection.class)
        .put(createType("map"), Map.class)
        .buildOrThrow();
  }

  private final ImmutableMap<Value, Class<?>> types;

  private StandardTypeResolver(ImmutableMap<Value, Class<?>> types) {
    this.types = types;
  }

  @Nullable
  @Override
  public Value resolveObjectType(Object obj, @Nullable Value checkedTypeValue) {
    if (checkedTypeValue != null && (obj instanceof Long || obj instanceof NullValue)) {
      return checkedTypeValue;
    }
    return resolveObjectType(obj);
  }

  @Nullable
  private Value resolveObjectType(Object obj) {
    for (Value type : types.keySet()) {
      Class<?> impl = types.get(type);
      // Generally, the type will be an instance of a class.
      if (impl.isInstance(obj)) {
        return type;
      }
    }
    // In the case 'type' values, the obj will be a api.expr.Value.
    if (obj instanceof Value) {
      Value objVal = (Value) obj;
      if (objVal.getKindCase() == KindCase.TYPE_VALUE) {
        return TYPE_VALUE;
      }
    }
    // Otherwise, this is a protobuf type.
    if (obj instanceof MessageOrBuilder) {
      MessageOrBuilder msg = (MessageOrBuilder) obj;
      return createType(msg.getDescriptorForType().getFullName());
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable Value adaptType(CelType type) {
    checkNotNull(type);
    // TODO: Add enum type support here.
    Value.Builder typeValue = Value.newBuilder();
    switch (type.kind()) {
      case OPAQUE:
      case STRUCT:
        return typeValue.setTypeValue(type.name()).build();
      case LIST:
        return typeValue.setTypeValue("list").build();
      case MAP:
        return typeValue.setTypeValue("map").build();
      case TYPE:
        CelType typeOfType = ((TypeType) type).type();
        if (typeOfType.kind() == CelKind.DYN) {
          return typeValue.setTypeValue("type").build();
        }
        return adaptType(typeOfType);
      case NULL_TYPE:
        return typeValue.setTypeValue("null_type").build();
      case DURATION:
        return typeValue.setTypeValue("google.protobuf.Duration").build();
      case TIMESTAMP:
        return typeValue.setTypeValue("google.protobuf.Timestamp").build();
      case BOOL:
        return typeValue.setTypeValue("bool").build();
      case BYTES:
        return typeValue.setTypeValue("bytes").build();
      case DOUBLE:
        return typeValue.setTypeValue("double").build();
      case INT:
        return typeValue.setTypeValue("int").build();
      case STRING:
        return typeValue.setTypeValue("string").build();
      case UINT:
        return typeValue.setTypeValue("uint").build();
      default:
        break;
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  @Deprecated
  public @Nullable Value adaptType(@Nullable Type type) {
    if (type == null) {
      return null;
    }
    // TODO: Add enum type support here.
    Value.Builder typeValue = Value.newBuilder();
    switch (type.getTypeKindCase()) {
      case ABSTRACT_TYPE:
        return typeValue.setTypeValue(type.getAbstractType().getName()).build();
      case MESSAGE_TYPE:
        return typeValue.setTypeValue(type.getMessageType()).build();
      case LIST_TYPE:
        return typeValue.setTypeValue("list").build();
      case MAP_TYPE:
        return typeValue.setTypeValue("map").build();
      case TYPE:
        Type typeOfType = type.getType();
        if (typeOfType.getTypeKindCase() == TypeKindCase.DYN) {
          return typeValue.setTypeValue("type").build();
        }
        return adaptType(typeOfType);
      case NULL:
        return typeValue.setTypeValue("null_type").build();
      case PRIMITIVE:
        return adaptPrimitive(type.getPrimitive());
      case WRAPPER:
        return adaptPrimitive(type.getWrapper());
      case WELL_KNOWN:
        switch (type.getWellKnown()) {
          case DURATION:
            return typeValue.setTypeValue("google.protobuf.Duration").build();
          case TIMESTAMP:
            return typeValue.setTypeValue("google.protobuf.Timestamp").build();
          default:
            break;
        }
        break;
      default:
        break;
    }
    return null;
  }

  @Nullable
  private static Value adaptPrimitive(PrimitiveType primitiveType) {
    Value.Builder typeValue = Value.newBuilder();
    switch (primitiveType) {
      case BOOL:
        return typeValue.setTypeValue("bool").build();
      case BYTES:
        return typeValue.setTypeValue("bytes").build();
      case DOUBLE:
        return typeValue.setTypeValue("double").build();
      case INT64:
        return typeValue.setTypeValue("int").build();
      case STRING:
        return typeValue.setTypeValue("string").build();
      case UINT64:
        return typeValue.setTypeValue("uint").build();
      default:
        break;
    }
    return null;
  }

  private static Value createType(String name) {
    return Value.newBuilder().setTypeValue(name).build();
  }
}
