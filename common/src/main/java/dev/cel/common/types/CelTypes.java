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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import dev.cel.common.annotations.Internal;
import java.util.Optional;

/** Utility class for working with {@code CelType}. */
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

  /** Checks if the fully-qualified protobuf type name is a wrapper type. */
  public static boolean isWrapperType(String typeName) {
    switch (typeName) {
      case BOOL_WRAPPER_MESSAGE:
      case BYTES_WRAPPER_MESSAGE:
      case DOUBLE_WRAPPER_MESSAGE:
      case FLOAT_WRAPPER_MESSAGE:
      case INT32_WRAPPER_MESSAGE:
      case INT64_WRAPPER_MESSAGE:
      case STRING_WRAPPER_MESSAGE:
      case UINT32_WRAPPER_MESSAGE:
      case UINT64_WRAPPER_MESSAGE:
        return true;
      default:
        return false;
    }
  }

  /**
   * Create an abstract type with an expected result type (first argument in the parameter) and the
   * argument types.
   *
   * <p>CEL Library Internals. Do Not Use.
   */
  @Internal
  public static OpaqueType createFunctionType(CelType resultType, Iterable<CelType> argumentTypes) {
    ImmutableList.Builder<CelType> arguments = ImmutableList.builder();
    arguments.add(resultType);
    arguments.addAll(argumentTypes);
    return OpaqueType.create("function", arguments.build());
  }

  /**
   * Method to adapt a simple {@code Type} into a {@code String} representation.
   *
   * <p>This method can also format global functions. See the {@link #formatFunction} methods for
   * richer control over function formatting.
   */
  public static String format(CelType type) {
    return format(type, /* typeParamToDyn= */ false);
  }

  static String format(CelType type, boolean typeParamToDyn) {
    if (type instanceof NullableType) {
      return String.format(
          "wrapper(%s)", format(((NullableType) type).targetType(), typeParamToDyn));
    }
    switch (type.kind()) {
      case DYN:
        return "dyn";
      case NULL_TYPE:
        return "null";
      case BOOL:
        return "bool";
      case INT:
        return "int";
      case UINT:
        return "uint";
      case DOUBLE:
        return "double";
      case STRING:
        return "string";
      case BYTES:
        return "bytes";
      case TIMESTAMP:
        return "google.protobuf.Timestamp";
      case DURATION:
        return "google.protobuf.Duration";
      case ANY:
        return "any";
      case LIST:
        ListType listType = (ListType) type;
        return String.format("list(%s)", format(listType.elemType(), typeParamToDyn));
      case MAP:
        MapType mapType = (MapType) type;
        return String.format(
            "map(%s, %s)",
            format(mapType.keyType(), typeParamToDyn), format(mapType.valueType(), typeParamToDyn));
      case TYPE:
        TypeType typeType = (TypeType) type;
        return String.format("type(%s)", format(typeType.type(), typeParamToDyn));
      case ERROR:
        return "*error*";
      case STRUCT:
        return type.name();
      case TYPE_PARAM:
        return typeParamToDyn ? "dyn" : type.name();
      case OPAQUE:
        if (type.name().equals("function")) {
          return formatFunction(
              type.parameters().get(0),
              type.parameters().subList(1, type.parameters().size()),
              false,
              typeParamToDyn);
        } else {
          String result = type.name();
          if (!type.parameters().isEmpty()) {
            result += formatTypeArgs(type.parameters(), typeParamToDyn);
          }
          return result;
        }
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
      CelType resultType, Iterable<CelType> argTypes, boolean isInstance, boolean typeParamToDyn) {
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
    return WELL_KNOWN_CEL_TYPE_MAP.containsKey(typeName);
  }

  public static Optional<CelType> getWellKnownCelType(String typeName) {
    return Optional.ofNullable(WELL_KNOWN_CEL_TYPE_MAP.getOrDefault(typeName, null));
  }

  private static String formatTypeArgs(Iterable<CelType> types, final boolean typeParamToDyn) {
    return String.format(
        "(%s)",
        Joiner.on(", ")
            .join(Iterables.transform(types, (CelType type) -> format(type, typeParamToDyn))));
  }

  private CelTypes() {}
}
