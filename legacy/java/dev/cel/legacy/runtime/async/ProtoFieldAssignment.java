package dev.cel.legacy.runtime.async;

import static dev.cel.legacy.runtime.async.Canonicalization.asInstanceOf;

import dev.cel.expr.Type;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import dev.cel.checker.Types;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.legacy.runtime.async.MessageProcessor.FieldAssigner;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import java.util.Map;

/**
 * Descriptor- and type-director assignment of values to proto fields.
 *
 * <p>This class provides tools for creating {@code Assigner} instances. An {@code Assigner} is the
 * mechanism that assigns a value to a proto field after first translating it from its canonical CEL
 * runtime representation to the type that is required for the field in question.
 */
final class ProtoFieldAssignment {

  /**
   * Represents the transformations to proto field value types from their corresponding canonical
   * CEL runtime representations. A {@link Protoizer} is roughly the inverse of a corresponding
   * {@link Canonicalization.Canonicalizer}.
   *
   * <p>If the value cannot be converted to its proto representation at runtime, a corresponding
   * {@code RuntimeException} is thrown.
   *
   * <p>Each instance of this interface corresponds to a possible type of proto message field, and T
   * is the Java type of that field, i.e., something acceptable to the {@code setField} or {@code
   * addRepeatedField} methods of {@code MessageOrBuilder}.
   */
  @Immutable
  @FunctionalInterface
  private interface Protoizer<T> {
    T protoize(Object canonicalValue);
  }

  /**
   * Returns an {@link FieldAssigner} for the described field that assumes that the value to be
   * assigned has the given CEL type.
   */
  public static FieldAssigner fieldValueAssigner(
      Metadata metadata, long id, FieldDescriptor fd, Type type) throws InterpreterException {
    if (fd.isMapField()) {
      return mapAssigner(metadata, id, fd, type);
    }
    if (fd.isRepeated()) {
      return listAssigner(metadata, id, fd, type);
    } else {
      Protoizer<?> protoizer = singleFieldProtoizer(metadata, id, fd, type);
      return (builder, value) -> builder.setField(fd, protoizer.protoize(value));
    }
  }

  /**
   * Returns an {@link FieldAssigner} for the described field which must be a repeated field. The
   * value to be assigned must be a CEL list, and the given CEL type should reflect that (unless it
   * is DYN).
   */
  private static FieldAssigner listAssigner(
      Metadata metadata, long id, FieldDescriptor fd, Type listType) throws InterpreterException {
    Type elementType = Types.DYN;
    if (!Types.isDynOrError(listType)) {
      if (listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE) {
        throw new InterpreterException.Builder("value for repeated field does not have type list")
            .setLocation(metadata, id)
            .build();
      }
      elementType = listType.getListType().getElemType();
    }
    Protoizer<?> protoizer = singleFieldProtoizer(metadata, id, fd, elementType);
    return (builder, listValue) -> {
      builder.clearField(fd);
      for (Object element : asInstanceOf(Iterable.class, listValue)) {
        builder.addRepeatedField(fd, protoizer.protoize(element));
      }
      return builder;
    };
  }

  /**
   * Returns an {@link FieldAssigner} for the described field which must be a map field. The value
   * to be assigned must be a CEL map, and the given CEL type should reflect that (unless it is
   * DYN).
   */
  private static FieldAssigner mapAssigner(
      Metadata metadata, long id, FieldDescriptor fd, Type mapType) throws InterpreterException {
    Descriptor entryDescriptor = fd.getMessageType();
    FieldDescriptor keyDescriptor = entryDescriptor.findFieldByNumber(1);
    FieldDescriptor valueDescriptor = entryDescriptor.findFieldByNumber(2);
    Type keyType = Types.DYN;
    Type valueType = Types.DYN;
    if (!Types.isDynOrError(mapType)) {
      switch (mapType.getTypeKindCase()) {
        case MAP_TYPE:
          keyType = mapType.getMapType().getKeyType();
          valueType = mapType.getMapType().getValueType();
          break;
        default:
          throw new InterpreterException.Builder("value for map field does not have map type")
              .setLocation(metadata, id)
              .build();
      }
    }
    Protoizer<?> keyProtoizer = singleFieldProtoizer(metadata, id, keyDescriptor, keyType);
    Protoizer<?> valueProtoizer = singleFieldProtoizer(metadata, id, valueDescriptor, valueType);
    MapEntry<Object, Object> protoMapEntry =
        MapEntry.newDefaultInstance(
            entryDescriptor,
            keyDescriptor.getLiteType(),
            getDefaultValueForMaybeMessage(keyDescriptor),
            valueDescriptor.getLiteType(),
            getDefaultValueForMaybeMessage(valueDescriptor));
    return (builder, value) -> {
      builder.clearField(fd);
      ((Map<?, ?>) asInstanceOf(Map.class, value))
          .entrySet()
          .forEach(
              entry ->
                  builder.addRepeatedField(
                      fd,
                      protoMapEntry.toBuilder()
                          .setKey(keyProtoizer.protoize(entry.getKey()))
                          .setValue(valueProtoizer.protoize(entry.getValue()))
                          .build()));
      return builder;
    };
  }

  /** Returns the default value for a field that can be a proto message */
  private static Object getDefaultValueForMaybeMessage(FieldDescriptor descriptor) {
    if (descriptor.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
      return DynamicMessage.getDefaultInstance(descriptor.getMessageType());
    }
    return descriptor.getDefaultValue();
  }

  /**
   * Returns a {@link Protoizer} for converting CEL values of the specified CEL type to the type
   * appropriate for the described field. The field cannot be a map field, and the returned
   * protoizer deals with only a single value even if the field is repeated.
   */
  private static Protoizer<?> singleFieldProtoizer(
      Metadata metadata, long id, FieldDescriptor fd, Type type) throws InterpreterException {
    // Possible future improvement: Consider verifying the CEL type.
    switch (fd.getType()) {
      case SFIXED32:
      case SINT32:
      case INT32:
        return value -> intCheckedCast(((Number) value).longValue());
      case FIXED32:
      case UINT32:
        return value -> unsignedIntCheckedCast(((Number) value).longValue());
      case FIXED64:
      case UINT64:
        return value -> ((Number) value).longValue();
      case ENUM:
        return value ->
            fd.getEnumType().findValueByNumberCreatingIfUnknown((int) ((Number) value).longValue());
      case FLOAT:
        return value -> doubleToFloat(((Number) value).doubleValue());
      case MESSAGE:
        return protoProtoizer(metadata, id, fd.getMessageType(), type);
      default:
        return x -> x;
    }
  }

  /**
   * Returns a protoizer that converts a CEL value to a proto message. Such protoizers are simply
   * the identity if the CEL value is already a proto message. However, if the target message type
   * is `Any` or any of the known wrapper protos or JSON protos, then the result will be a protoizer
   * that wraps its non-proto argument accordingly.
   */
  private static Protoizer<? extends Message> protoProtoizer(
      Metadata metadata, long id, Descriptor d, Type type) throws InterpreterException {
    switch (d.getFullName()) {
      case "google.protobuf.Any":
        return anyProtoizer(metadata, id, type);
      case "google.protobuf.Value":
        return jsonValueProtoizer(metadata, id, type);
      case "google.protobuf.ListValue":
        return jsonListProtoizer(metadata, id, type);
      case "google.protobuf.Struct":
        return jsonStructProtoizer(metadata, id, type);
      case "google.protobuf.Int64Value":
        return ProtoFieldAssignment::toInt64Value;
      case "google.protobuf.UInt64Value":
        return ProtoFieldAssignment::toUInt64Value;
      case "google.protobuf.Int32Value":
        return ProtoFieldAssignment::toInt32Value;
      case "google.protobuf.UInt32Value":
        return ProtoFieldAssignment::toUInt32Value;
      case "google.protobuf.DoubleValue":
        return ProtoFieldAssignment::toDoubleValue;
      case "google.protobuf.FloatValue":
        return ProtoFieldAssignment::toFloatValue;
      case "google.protobuf.BoolValue":
        return ProtoFieldAssignment::toBoolValue;
      case "google.protobuf.StringValue":
        return ProtoFieldAssignment::toStringValue;
      case "google.protobuf.BytesValue":
        return ProtoFieldAssignment::toBytesValue;
      default:
        return x -> asInstanceOf(Message.class, x);
    }
  }

  /**
   * Returns a protoizer that wraps the value in an {@link Any}, after suitably wrapping non-proto
   * values in wrapper protos.
   */
  private static Protoizer<Any> anyProtoizer(Metadata metadata, long id, Type type)
      throws InterpreterException {
    Protoizer<? extends Message> messageProtoizer = wrapIfNecessaryProtoizer(metadata, id, type);
    return x -> Any.pack(messageProtoizer.protoize(x));
  }

  /**
   * Returns a protoizer that wraps values of the given CEL type in proto wrappes that make them
   * suitable for further wrapping them with {@link Any}. Values that are already protos are not
   * further wrapped.
   *
   * <p>Notice that thanks to the knowledge of the CEL type it is possible to distinguish between
   * signed and unsigned values even though the CEL runtime representation does not carry such
   * information.
   *
   * <p>If the CEL type is not known (i.e., DYN), then a fallback protoizer that uses only dynamic
   * information is returned.
   */
  private static Protoizer<? extends Message> wrapIfNecessaryProtoizer(
      Metadata metadata, long id, Type type) throws InterpreterException {
    switch (type.getTypeKindCase()) {
      case LIST_TYPE:
        return jsonListProtoizer(metadata, id, type);
      case MAP_TYPE:
        return jsonStructProtoizer(metadata, id, type);
      case PRIMITIVE:
        {
          switch (type.getPrimitive()) {
            case BOOL:
              return ProtoFieldAssignment::toBoolValue;
            case INT64:
              return ProtoFieldAssignment::toInt64Value;
            case UINT64:
              return ProtoFieldAssignment::toUInt64Value;
            case DOUBLE:
              return ProtoFieldAssignment::toDoubleValue;
            case STRING:
              return ProtoFieldAssignment::toStringValue;
            case BYTES:
              return ProtoFieldAssignment::toBytesValue;
            default:
              return ProtoFieldAssignment::dynamicWrapIfNecessary;
          }
        }
      case MESSAGE_TYPE:
        return x -> asInstanceOf(Message.class, x);
      default:
        return ProtoFieldAssignment::dynamicWrapIfNecessary;
    }
  }

  /** Fallback protoizer returned by {@code dynamicWrapIfNecessary} in the dynamic case. */
  private static Message dynamicWrapIfNecessary(Object value) {
    if (value instanceof Message) {
      return (Message) value;
    }
    if (value instanceof Long) {
      return Int64Value.of((Long) value);
    }
    if (value instanceof Double) {
      return DoubleValue.of((Double) value);
    }
    if (value instanceof Boolean) {
      return BoolValue.of((Boolean) value);
    }
    if (value instanceof String) {
      return StringValue.of((String) value);
    }
    if (value instanceof ByteString) {
      return BytesValue.of((ByteString) value);
    }
    if (value instanceof Iterable) {
      return dynamicJsonList(value);
    }
    if (value instanceof Map) {
      return dynamicJsonStruct(value);
    }
    throw new IllegalArgumentException(
        "Unsupported type to pack to Any:" + value.getClass().getSimpleName());
  }

  /**
   * Returns a protoizer that converts a CEL value to a JSON value, i.e., an instance of {@link
   * Value}.
   *
   * <p>The construction is type-directed based on the known CEL type of the incoming value, falling
   * back to a fully dynamic mechanism where type DYN is encountered.
   */
  private static Protoizer<Value> jsonValueProtoizer(Metadata metadata, long id, Type type)
      throws InterpreterException {
    switch (type.getTypeKindCase()) {
      case NULL:
        return ignored -> dynamicJsonValue(null);
      case LIST_TYPE:
        {
          Protoizer<ListValue> listProtoizer = jsonListProtoizer(metadata, id, type);
          return value -> Value.newBuilder().setListValue(listProtoizer.protoize(value)).build();
        }
      case MAP_TYPE:
        {
          Protoizer<Struct> structProtoizer = jsonStructProtoizer(metadata, id, type);
          return value ->
              Value.newBuilder().setStructValue(structProtoizer.protoize(value)).build();
        }
      case PRIMITIVE:
        {
          switch (type.getPrimitive()) {
            case BOOL:
              return x -> Value.newBuilder().setBoolValue(asInstanceOf(Boolean.class, x)).build();
            case INT64:
            case UINT64:
            case DOUBLE:
              return x ->
                  Value.newBuilder()
                      .setNumberValue(asInstanceOf(Number.class, x).doubleValue())
                      .build();
            case STRING:
              return x -> Value.newBuilder().setStringValue(asInstanceOf(String.class, x)).build();
            default:
              return ProtoFieldAssignment::dynamicJsonValue;
          }
        }
      default:
        return ProtoFieldAssignment::dynamicJsonValue;
    }
  }

  /**
   * Returns a protoizer that turns a CEL list into a JSON list, i.e., an instance of {@link
   * ListValue}.
   *
   * <p>The construction is type-directed based on the known CEL type of the incoming value, falling
   * back to a fully dynamic mechanism where type DYN is encountered.
   */
  private static Protoizer<ListValue> jsonListProtoizer(Metadata metadata, long id, Type type)
      throws InterpreterException {
    FieldAssigner assigner = listAssigner(metadata, id, LIST_VALUE_VALUES, type);
    return listValue -> {
      ListValue.Builder builder = ListValue.newBuilder();
      assigner.assign(builder, listValue);
      return builder.build();
    };
  }

  /**
   * Returns a protoizer that turns a CEL map into a JSON struct, i.e., an instance of {@link
   * Struct}.
   *
   * <p>The construction is type-directed based on the known CEL type of the incoming value, falling
   * back to a fully dynamic mechanism where type DYN is encountered.
   */
  private static Protoizer<Struct> jsonStructProtoizer(Metadata metadata, long id, Type type)
      throws InterpreterException {
    FieldAssigner assigner = mapAssigner(metadata, id, STRUCT_FIELDS, type);
    return mapValue -> {
      Struct.Builder builder = Struct.newBuilder();
      assigner.assign(builder, mapValue);
      return builder.build();
    };
  }

  /** Fallback protoizer returned by {@code jsonValueProtoizer} in the dynamic case. */
  private static Value dynamicJsonValue(Object object) {
    Value.Builder builder = Value.newBuilder();
    if (object == null || object instanceof NullValue) {
      builder.setNullValue(NullValue.NULL_VALUE);
    } else if (object instanceof Boolean) {
      builder.setBoolValue((Boolean) object);
    } else if (object instanceof Number) {
      builder.setNumberValue(((Number) object).doubleValue());
    } else if (object instanceof String) {
      builder.setStringValue((String) object);
    } else if (object instanceof Map) {
      builder.setStructValue(dynamicJsonStruct(object));
    } else if (object instanceof Iterable) {
      builder.setListValue(dynamicJsonList(object));
    } else {
      throw new IllegalArgumentException("[internal] value cannot be converted to JSON");
    }
    return builder.build();
  }

  /**
   * Converts a CEL value to a JSON list (i.e., a {@link ListValue} instance). No exception is
   * expected to be thrown since a suitable CEL type is passed when constructing the protoizer.
   */
  private static ListValue dynamicJsonList(Object object) {
    try {
      return jsonListProtoizer(DUMMY_METADATA, 0, DYNAMIC_JSON_LIST_TYPE).protoize(object);
    } catch (InterpreterException e) {
      throw new AssertionError("unexpected exception", e);
    }
  }

  /**
   * Converts a CEL value to a JSON struct (i.e., a {@link Struct} instance). No exception is
   * expected to be thrown since a suitable CEL type is passed when constructing the protoizer.
   */
  private static Struct dynamicJsonStruct(Object object) {
    try {
      return jsonStructProtoizer(DUMMY_METADATA, 0, DYNAMIC_JSON_MAP_TYPE).protoize(object);
    } catch (InterpreterException e) {
      throw new AssertionError("unexpected exception", e);
    }
  }

  /** Converts a CEL value to a wrapped int64. */
  private static Message toInt64Value(Object x) {
    return Int64Value.of(asInstanceOf(Long.class, x));
  }

  /** Converts a CEL value to a wrapped uint64. */
  private static Message toUInt64Value(Object x) {
    if (x instanceof UnsignedLong) {
      return UInt64Value.of(asInstanceOf(UnsignedLong.class, x).longValue());
    }
    return UInt64Value.of(asInstanceOf(Long.class, x));
  }

  /** Converts a CEL value to a wrapped int32. */
  private static Message toInt32Value(Object x) {
    return Int32Value.of(intCheckedCast(asInstanceOf(Long.class, x)));
  }

  /** Converts a CEL value to a wrapped uint32. */
  private static Message toUInt32Value(Object x) {
    if (x instanceof UnsignedLong) {
      return UInt64Value.of(
          unsignedIntCheckedCast(asInstanceOf(UnsignedLong.class, x).longValue()));
    }
    return UInt32Value.of(unsignedIntCheckedCast(asInstanceOf(Long.class, x)));
  }

  /** Converts a CEL value to a wrapped boolean. */
  private static Message toBoolValue(Object x) {
    return BoolValue.of(asInstanceOf(Boolean.class, x));
  }

  /** Converts a CEL value to a wrapped double. */
  private static Message toDoubleValue(Object x) {
    return DoubleValue.of(asInstanceOf(Double.class, x));
  }

  /** Converts a CEL value to a wrapped float. */
  private static Message toFloatValue(Object x) {
    return FloatValue.of(doubleToFloat(asInstanceOf(Double.class, x)));
  }

  /** Converts a CEL value to a wrapped string. */
  private static Message toStringValue(Object x) {
    return StringValue.of(asInstanceOf(String.class, x));
  }

  /** Converts a CEL value to a wrapped bytes value. */
  private static Message toBytesValue(Object x) {
    return BytesValue.of(asInstanceOf(ByteString.class, x));
  }

  /**
   * Coerces a {@code double} into a {@code float}, throwing an {@link IllegalArgumentException}
   * when it does not fit.
   */
  private static float doubleToFloat(double d) {
    float f = (float) d;
    if (d != f) {
      throw new IllegalArgumentException("double out of range for conversion to float");
    }
    return f;
  }

  private static final FieldDescriptor STRUCT_FIELDS =
      Struct.getDescriptor().findFieldByName("fields");
  private static final FieldDescriptor LIST_VALUE_VALUES =
      ListValue.getDescriptor().findFieldByName("values");
  private static final Type DYNAMIC_JSON_MAP_TYPE = Types.createMap(Types.STRING, Types.DYN);
  private static final Type DYNAMIC_JSON_LIST_TYPE = Types.createList(Types.DYN);
  private static final Metadata DUMMY_METADATA =
      new Metadata() {
        @Override
        public String getLocation() {
          return "";
        }

        @Override
        public int getPosition(long exprId) {
          return 0;
        }
      };

  private static int intCheckedCast(long value) {
    try {
      return Ints.checkedCast(value);
    } catch (IllegalArgumentException e) {
      throw new CelRuntimeException(e, CelErrorCode.NUMERIC_OVERFLOW);
    }
  }

  private static int unsignedIntCheckedCast(long value) {
    try {
      return UnsignedInts.checkedCast(value);
    } catch (IllegalArgumentException e) {
      throw new CelRuntimeException(e, CelErrorCode.NUMERIC_OVERFLOW);
    }
  }

  private ProtoFieldAssignment() {}
}
