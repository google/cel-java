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

package dev.cel.common.types;

import static com.google.common.collect.ImmutableList.toImmutableList;

import dev.cel.expr.Type;
import dev.cel.expr.Type.AbstractType;
import dev.cel.expr.Type.PrimitiveType;
import dev.cel.expr.Type.WellKnownType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Empty;
import com.google.protobuf.NullValue;

/**
 * Utility class for working with {@link Type}.
 *
 * <p>This is equivalent to {@link CelTypes}, except this works specifically with canonical CEL expr
 * protos.
 */
public final class CelProtoTypes {

  public static final Type ERROR = Type.newBuilder().setError(Empty.getDefaultInstance()).build();
  public static final Type DYN = Type.newBuilder().setDyn(Empty.getDefaultInstance()).build();
  public static final Type NULL_TYPE = Type.newBuilder().setNull(NullValue.NULL_VALUE).build();
  public static final Type BOOL = create(PrimitiveType.BOOL);
  public static final Type BYTES = create(PrimitiveType.BYTES);
  public static final Type STRING = create(PrimitiveType.STRING);
  public static final Type DOUBLE = create(PrimitiveType.DOUBLE);
  public static final Type UINT64 = create(PrimitiveType.UINT64);
  public static final Type INT64 = create(PrimitiveType.INT64);
  public static final Type ANY = create(WellKnownType.ANY);
  public static final Type TIMESTAMP = create(WellKnownType.TIMESTAMP);
  public static final Type DURATION = create(WellKnownType.DURATION);

  private static final ImmutableMap<CelKind, Type> SIMPLE_CEL_KIND_TO_TYPE =
      ImmutableMap.<CelKind, Type>builder()
          .put(CelKind.ERROR, ERROR)
          .put(CelKind.DYN, DYN)
          .put(CelKind.ANY, ANY)
          .put(CelKind.BOOL, BOOL)
          .put(CelKind.BYTES, BYTES)
          .put(CelKind.DOUBLE, DOUBLE)
          .put(CelKind.DURATION, DURATION)
          .put(CelKind.INT, INT64)
          .put(CelKind.NULL_TYPE, NULL_TYPE)
          .put(CelKind.STRING, STRING)
          .put(CelKind.TIMESTAMP, TIMESTAMP)
          .put(CelKind.UINT, UINT64)
          .buildOrThrow();

  private static final ImmutableMap<Type, CelType> PROTOBUF_TYPE_TO_CEL_TYPE_MAP =
      ImmutableMap.<Type, CelType>builder()
          .put(BOOL, SimpleType.BOOL)
          .put(BYTES, SimpleType.BYTES)
          .put(DOUBLE, SimpleType.DOUBLE)
          .put(INT64, SimpleType.INT)
          .put(STRING, SimpleType.STRING)
          .put(UINT64, SimpleType.UINT)
          .put(ANY, SimpleType.ANY)
          .put(DURATION, SimpleType.DURATION)
          .put(TIMESTAMP, SimpleType.TIMESTAMP)
          .put(DYN, SimpleType.DYN)
          .put(NULL_TYPE, SimpleType.NULL_TYPE)
          .put(ERROR, SimpleType.ERROR)
          .buildOrThrow();

  /** Create a primitive {@code Type}. */
  public static Type create(PrimitiveType type) {
    return Type.newBuilder().setPrimitive(type).build();
  }

  /** Create a well-known {@code Type}. */
  public static Type create(WellKnownType type) {
    return Type.newBuilder().setWellKnown(type).build();
  }

  /** Create a type {@code Type}. */
  public static Type create(Type target) {
    return Type.newBuilder().setType(target).build();
  }

  /** Create a list with {@code elemType}. */
  public static Type createList(Type elemType) {
    return Type.newBuilder().setListType(Type.ListType.newBuilder().setElemType(elemType)).build();
  }

  /** Create a map with {@code keyType} and {@code valueType}. */
  public static Type createMap(Type keyType, Type valueType) {
    return Type.newBuilder()
        .setMapType(Type.MapType.newBuilder().setKeyType(keyType).setValueType(valueType))
        .build();
  }

  /** Create a message {@code Type} for {@code messageName}. */
  public static Type createMessage(String messageName) {
    return Type.newBuilder().setMessageType(messageName).build();
  }

  /**
   * @deprecated Use {@link CelProtoMessageTypes#createMessage} instead.
   */
  @Deprecated
  public static Type createMessage(Descriptor descriptor) {
    return createMessage(descriptor.getFullName());
  }

  /** Create a type param {@code Type}. */
  public static Type createTypeParam(String name) {
    return Type.newBuilder().setTypeParam(name).build();
  }

  /** Create a wrapper type for the {@code primitive}. */
  public static Type createWrapper(PrimitiveType primitive) {
    return Type.newBuilder().setWrapper(primitive).build();
  }

  /** Create a wrapper type where the input is a {@code Type} of primitive types. */
  public static Type createWrapper(Type type) {
    Preconditions.checkArgument(type.getTypeKindCase() == Type.TypeKindCase.PRIMITIVE);
    return createWrapper(type.getPrimitive());
  }

  /**
   * Create an abstract type indicating that the parameterized type may be contained within the
   * object.
   */
  public static Type createOptionalType(Type paramType) {
    return Type.newBuilder()
        .setAbstractType(
            AbstractType.newBuilder()
                .setName(OptionalType.NAME)
                .addParameterTypes(paramType)
                .build())
        .build();
  }

  /** Checks if the provided parameter is an optional type */
  public static boolean isOptionalType(Type type) {
    return type.hasAbstractType() && type.getAbstractType().getName().equals(OptionalType.NAME);
  }

  /**
   * Method to adapt a simple {@code Type} into a {@code String} representation.
   *
   * <p>This method can also format global functions. See the {@link CelTypes#formatFunction}
   * methods for richer control over function formatting.
   */
  public static String format(Type type) {
    return CelTypes.format(typeToCelType(type), /* typeParamToDyn= */ false);
  }

  /** Converts a Protobuf type into CEL native type. */
  public static Type celTypeToType(CelType celType) {
    Type type = SIMPLE_CEL_KIND_TO_TYPE.get(celType.kind());
    if (type != null) {
      if (celType instanceof NullableType) {
        return createWrapper(type);
      }
      return type;
    }

    switch (celType.kind()) {
      case UNSPECIFIED:
        return Type.getDefaultInstance();
      case LIST:
        ListType listType = (ListType) celType;
        if (listType.hasElemType()) {
          return createList(celTypeToType(listType.elemType()));
        } else {
          // TODO: Exists for compatibility reason only. Remove after callers have been
          // migrated.
          return Type.newBuilder().setListType(Type.ListType.getDefaultInstance()).build();
        }
      case MAP:
        MapType mapType = (MapType) celType;
        return createMap(celTypeToType(mapType.keyType()), celTypeToType(mapType.valueType()));
      case OPAQUE:
        if (celType.name().equals("function")) {
          Type.FunctionType.Builder functionBuilder = Type.FunctionType.newBuilder();
          if (!celType.parameters().isEmpty()) {
            functionBuilder
                .setResultType(celTypeToType(celType.parameters().get(0)))
                .addAllArgTypes(
                    celType.parameters().stream()
                        .skip(1)
                        .map(CelProtoTypes::celTypeToType)
                        .collect(toImmutableList()));
          }
          return Type.newBuilder().setFunction(functionBuilder).build();
        } else {
          return Type.newBuilder()
              .setAbstractType(
                  Type.AbstractType.newBuilder()
                      .setName(celType.name())
                      .addAllParameterTypes(
                          celType.parameters().stream()
                              .map(CelProtoTypes::celTypeToType)
                              .collect(toImmutableList())))
              .build();
        }
      case STRUCT:
        return createMessage(celType.name());
      case TYPE:
        TypeType typeType = (TypeType) celType;
        return create(celTypeToType(typeType.type()));
      case TYPE_PARAM:
        return createTypeParam(celType.name());
      default:
        throw new IllegalArgumentException(String.format("Unsupported type: %s", celType));
    }
  }

  /** Converts a Protobuf type to CEL native type. */
  public static CelType typeToCelType(Type type) {
    CelType celType = PROTOBUF_TYPE_TO_CEL_TYPE_MAP.get(type);
    if (celType != null) {
      return celType;
    }

    switch (type.getTypeKindCase()) {
      case TYPEKIND_NOT_SET:
        return UnspecifiedType.create();
      case WRAPPER:
        return NullableType.create(typeToCelType(create(type.getWrapper())));
      case MESSAGE_TYPE:
        return StructTypeReference.create(type.getMessageType());
      case LIST_TYPE:
        Type.ListType listType = type.getListType();
        if (listType.hasElemType()) {
          return ListType.create(typeToCelType(listType.getElemType()));
        } else {
          // TODO: Exists for compatibility reason only. Remove after callers have been
          // migrated.
          return ListType.create();
        }
      case MAP_TYPE:
        Type.MapType mapType = type.getMapType();
        return MapType.create(
            typeToCelType(mapType.getKeyType()), typeToCelType(mapType.getValueType()));
      case TYPE_PARAM:
        return TypeParamType.create(type.getTypeParam());
      case ABSTRACT_TYPE:
        Type.AbstractType abstractType = type.getAbstractType();
        ImmutableList<CelType> params =
            abstractType.getParameterTypesList().stream()
                .map(CelProtoTypes::typeToCelType)
                .collect(toImmutableList());
        if (abstractType.getName().equals(OptionalType.NAME)) {
          return OptionalType.create(params.get(0));
        }
        return OpaqueType.create(abstractType.getName(), params);
      case TYPE:
        return TypeType.create(typeToCelType(type.getType()));
      case FUNCTION:
        Type.FunctionType functionType = type.getFunction();
        return CelTypes.createFunctionType(
            typeToCelType(functionType.getResultType()),
            functionType.getArgTypesList().stream()
                .map(CelProtoTypes::typeToCelType)
                .collect(toImmutableList()));
      default:
        // Add more cases as needed.
        throw new IllegalArgumentException(String.format("Unsupported type: %s", type));
    }
  }

  private CelProtoTypes() {}
}
