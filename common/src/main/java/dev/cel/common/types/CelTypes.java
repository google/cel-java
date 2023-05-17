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

package dev.cel.common.types;

import static com.google.common.collect.ImmutableList.toImmutableList;

import dev.cel.expr.Type;
import dev.cel.expr.Type.AbstractType;
import dev.cel.expr.Type.PrimitiveType;
import dev.cel.expr.Type.WellKnownType;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Empty;
import com.google.protobuf.NullValue;
import dev.cel.common.annotations.Internal;
import java.util.Optional;

/** Utility class for working with {@link Type}. */
public final class CelTypes {

  // Message type names with well-known type equivalents or special handling.
  public static final String ANY_MESSAGE = "google.protobuf.Any";
  public static final String DURATION_MESSAGE = "google.protobuf.Duration";
  public static final String LIST_VALUE_MESSAGE = "google.protobuf.ListValue";
  public static final String STRUCT_MESSAGE = "google.protobuf.Struct";
  public static final String TIMESTAMP_MESSAGE = "google.protobuf.Timestamp";
  public static final String VALUE_MESSAGE = "google.protobuf.Value";

  // Message names for wrapper types.
  public static final String BOOL_WRAPPER_MESSAGE = "google.protobuf.BoolValue";
  public static final String BYTES_WRAPPER_MESSAGE = "google.protobuf.BytesValue";
  public static final String DOUBLE_WRAPPER_MESSAGE = "google.protobuf.DoubleValue";
  public static final String FLOAT_WRAPPER_MESSAGE = "google.protobuf.FloatValue";
  public static final String INT32_WRAPPER_MESSAGE = "google.protobuf.Int32Value";
  public static final String INT64_WRAPPER_MESSAGE = "google.protobuf.Int64Value";
  public static final String STRING_WRAPPER_MESSAGE = "google.protobuf.StringValue";
  public static final String UINT32_WRAPPER_MESSAGE = "google.protobuf.UInt32Value";
  public static final String UINT64_WRAPPER_MESSAGE = "google.protobuf.UInt64Value";

  // Static types.
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

  /** Map of well-known proto messages and their CEL {@code Type} equivalents. */
  static final ImmutableMap<String, Type> WELL_KNOWN_TYPE_MAP =
      ImmutableMap.<String, Type>builder()
          .put(DOUBLE_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.DOUBLE))
          .put(FLOAT_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.DOUBLE))
          .put(INT64_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.INT64))
          .put(INT32_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.INT64))
          .put(UINT64_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.UINT64))
          .put(UINT32_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.UINT64))
          .put(BOOL_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.BOOL))
          .put(STRING_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.STRING))
          .put(BYTES_WRAPPER_MESSAGE, CelTypes.createWrapper(CelTypes.BYTES))
          .put(TIMESTAMP_MESSAGE, CelTypes.TIMESTAMP)
          .put(DURATION_MESSAGE, CelTypes.DURATION)
          .put(STRUCT_MESSAGE, CelTypes.createMap(CelTypes.STRING, CelTypes.DYN))
          .put(VALUE_MESSAGE, CelTypes.DYN)
          .put(LIST_VALUE_MESSAGE, CelTypes.createList(CelTypes.DYN))
          .put(ANY_MESSAGE, CelTypes.ANY)
          .buildOrThrow();

  private static final ImmutableMap<String, CelType> WELL_KNOWN_CEL_TYPE_MAP =
      ImmutableMap.<String, CelType>builder()
          .put(BOOL_WRAPPER_MESSAGE, NullableType.create(SimpleType.BOOL))
          .put(BYTES_WRAPPER_MESSAGE, NullableType.create(SimpleType.BYTES))
          .put(FLOAT_WRAPPER_MESSAGE, NullableType.create(SimpleType.DOUBLE))
          .put(DOUBLE_WRAPPER_MESSAGE, NullableType.create(SimpleType.DOUBLE))
          .put(INT32_WRAPPER_MESSAGE, NullableType.create(SimpleType.INT))
          .put(INT64_WRAPPER_MESSAGE, NullableType.create(SimpleType.INT))
          .put(STRING_WRAPPER_MESSAGE, NullableType.create(SimpleType.STRING))
          .put(UINT32_WRAPPER_MESSAGE, NullableType.create(SimpleType.UINT))
          .put(UINT64_WRAPPER_MESSAGE, NullableType.create(SimpleType.UINT))
          .put(ANY_MESSAGE, SimpleType.ANY)
          .put(DURATION_MESSAGE, SimpleType.DURATION)
          .put(TIMESTAMP_MESSAGE, SimpleType.TIMESTAMP)
          .put(LIST_VALUE_MESSAGE, ListType.create(SimpleType.DYN))
          .put(STRUCT_MESSAGE, MapType.create(SimpleType.STRING, SimpleType.DYN))
          .put(VALUE_MESSAGE, SimpleType.DYN)
          .buildOrThrow();

  static final ImmutableMap<CelKind, Type> SIMPLE_CEL_KIND_TO_TYPE =
      ImmutableMap.<CelKind, Type>builder()
          .put(CelKind.ERROR, CelTypes.ERROR)
          .put(CelKind.DYN, CelTypes.DYN)
          .put(CelKind.ANY, CelTypes.ANY)
          .put(CelKind.BOOL, CelTypes.BOOL)
          .put(CelKind.BYTES, CelTypes.BYTES)
          .put(CelKind.DOUBLE, CelTypes.DOUBLE)
          .put(CelKind.DURATION, CelTypes.DURATION)
          .put(CelKind.INT, CelTypes.INT64)
          .put(CelKind.NULL_TYPE, CelTypes.NULL_TYPE)
          .put(CelKind.STRING, CelTypes.STRING)
          .put(CelKind.TIMESTAMP, CelTypes.TIMESTAMP)
          .put(CelKind.UINT, CelTypes.UINT64)
          .buildOrThrow();

  private static final ImmutableMap<Type, CelType> PROTOBUF_TYPE_TO_CEL_TYPE_MAP =
      ImmutableMap.<Type, CelType>builder()
          .put(CelTypes.BOOL, SimpleType.BOOL)
          .put(CelTypes.BYTES, SimpleType.BYTES)
          .put(CelTypes.DOUBLE, SimpleType.DOUBLE)
          .put(CelTypes.INT64, SimpleType.INT)
          .put(CelTypes.STRING, SimpleType.STRING)
          .put(CelTypes.UINT64, SimpleType.UINT)
          .put(CelTypes.ANY, SimpleType.ANY)
          .put(CelTypes.DURATION, SimpleType.DURATION)
          .put(CelTypes.TIMESTAMP, SimpleType.TIMESTAMP)
          .put(CelTypes.DYN, SimpleType.DYN)
          .put(CelTypes.NULL_TYPE, SimpleType.NULL_TYPE)
          .put(CelTypes.ERROR, SimpleType.ERROR)
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

  /** Create a message {@code Type} for {@code Descriptor}. */
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
   * <p>This method can also format global functions. See the {@link #formatFunction} methods for
   * richer control over function formatting.
   */
  public static String format(Type type) {
    return format(type, /* typeParamToDyn= */ false);
  }

  private static String format(Type type, boolean typeParamToDyn) {
    switch (type.getTypeKindCase()) {
      case DYN:
        return "dyn";
      case NULL:
        return "null";
      case PRIMITIVE:
        switch (type.getPrimitive()) {
          case BOOL:
            return "bool";
          case INT64:
            return "int";
          case UINT64:
            return "uint";
          case DOUBLE:
            return "double";
          case STRING:
            return "string";
          case BYTES:
            return "bytes";
          default:
            break;
        }
        break;
      case WELL_KNOWN:
        switch (type.getWellKnown()) {
          case TIMESTAMP:
            return "google.protobuf.Timestamp";
          case DURATION:
            return "google.protobuf.Duration";
          case ANY:
            return "any";
          default:
            break;
        }
        break;
      case LIST_TYPE:
        return String.format("list(%s)", format(type.getListType().getElemType(), typeParamToDyn));
      case MAP_TYPE:
        return String.format(
            "map(%s, %s)",
            format(type.getMapType().getKeyType(), typeParamToDyn),
            format(type.getMapType().getValueType(), typeParamToDyn));
      case TYPE:
        return String.format("type(%s)", format(type.getType(), typeParamToDyn));
      case WRAPPER:
        return String.format("wrapper(%s)", format(create(type.getWrapper()), typeParamToDyn));
      case ERROR:
        return "*error*";
      case MESSAGE_TYPE:
        return type.getMessageType();
      case TYPE_PARAM:
        return typeParamToDyn ? "dyn" : type.getTypeParam();
      case FUNCTION:
        return formatFunction(
            type.getFunction().getResultType(),
            type.getFunction().getArgTypesList(),
            false,
            typeParamToDyn);
      case ABSTRACT_TYPE:
        String result = type.getAbstractType().getName();
        if (type.getAbstractType().getParameterTypesCount() > 0) {
          result += formatTypeArgs(type.getAbstractType().getParameterTypesList(), typeParamToDyn);
        }
        return result;
      default:
        break;
    }
    return "<unknown type>";
  }

  /**
   * Format a function signature string from the input {@code argTypes} and {@code resultType}.
   *
   * <p>When {@code isInstance} is {@code true}, the {@code argTypes[0]} type is used as the
   * receiver type.
   *
   * <p>When {@code resultType} is {@code null}, the function signature omits the result type. This
   * is useful for computing overload signatures.
   *
   * <p>When {@code typeParamToDyn} is {@code true}, parameterized type argument are represented as
   * {@code Types.DYN} values.
   */
  public static String formatFunction(
      Type resultType, Iterable<Type> argTypes, boolean isInstance, boolean typeParamToDyn) {
    String argString;
    if (isInstance) {
      argString =
          format(Iterables.get(argTypes, 0), typeParamToDyn)
              + "."
              + formatTypeArgs(Iterables.skip(argTypes, 1), typeParamToDyn);
    } else {
      argString = formatTypeArgs(argTypes, typeParamToDyn);
    }
    if (resultType == null) {
      return argString;
    } else {
      return argString + " -> " + format(resultType, typeParamToDyn);
    }
  }

  public static boolean isWellKnownType(String typeName) {
    return WELL_KNOWN_TYPE_MAP.containsKey(typeName);
  }

  public static Optional<CelType> getWellKnownCelType(String typeName) {
    return Optional.ofNullable(WELL_KNOWN_CEL_TYPE_MAP.getOrDefault(typeName, null));
  }

  /** Converts a Protobuf type into CEL native type. */
  @Internal
  public static Type celTypeToType(CelType celType) {
    Type type = SIMPLE_CEL_KIND_TO_TYPE.get(celType.kind());
    if (type != null) {
      if (celType instanceof NullableType) {
        return CelTypes.createWrapper(type);
      }
      return type;
    }

    switch (celType.kind()) {
      case LIST:
        ListType listType = (ListType) celType;
        return CelTypes.createList(celTypeToType(listType.elemType()));
      case MAP:
        MapType mapType = (MapType) celType;
        return CelTypes.createMap(
            celTypeToType(mapType.keyType()), celTypeToType(mapType.valueType()));
      case OPAQUE:
        return Type.newBuilder()
            .setAbstractType(
                Type.AbstractType.newBuilder()
                    .setName(celType.name())
                    .addAllParameterTypes(
                        celType.parameters().stream()
                            .map(CelTypes::celTypeToType)
                            .collect(toImmutableList())))
            .build();
      case STRUCT:
        return CelTypes.createMessage(celType.name());
      case TYPE:
        TypeType typeType = (TypeType) celType;
        return CelTypes.create(celTypeToType(typeType.type()));
      case TYPE_PARAM:
        return CelTypes.createTypeParam(celType.name());
      default:
        throw new IllegalArgumentException(String.format("Unsupported type: %s", celType));
    }
  }

  /** Converts a Protobuf type to CEL native type. */
  @Internal
  public static CelType typeToCelType(Type type) {
    CelType celType = PROTOBUF_TYPE_TO_CEL_TYPE_MAP.get(type);
    if (celType != null) {
      return celType;
    }

    switch (type.getTypeKindCase()) {
      case WRAPPER:
        return NullableType.create(typeToCelType(CelTypes.create(type.getWrapper())));
      case MESSAGE_TYPE:
        return StructTypeReference.create(type.getMessageType());
      case LIST_TYPE:
        Type.ListType listType = type.getListType();
        return ListType.create(typeToCelType(listType.getElemType()));
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
                .map(CelTypes::typeToCelType)
                .collect(toImmutableList());
        if (abstractType.getName().equals(OptionalType.NAME)) {
          return OptionalType.create(params.get(0));
        }
        return OpaqueType.create(abstractType.getName(), params);
      case TYPE:
        return TypeType.create(typeToCelType(type.getType()));
      default:
        // Add more cases as needed.
        throw new IllegalArgumentException(String.format("Unsupported type: %s", type));
    }
  }

  private static String formatTypeArgs(Iterable<Type> types, final boolean typeParamToDyn) {
    return String.format(
        "(%s)",
        Joiner.on(", ")
            .join(Iterables.transform(types, (Type type) -> format(type, typeParamToDyn))));
  }

  private CelTypes() {}
}
