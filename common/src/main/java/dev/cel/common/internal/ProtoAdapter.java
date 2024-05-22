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

package dev.cel.common.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import dev.cel.expr.ExprValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelProtoJsonAdapter;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.nullness.Nullable;

/**
 * The {@code ProtoAdapter} utilities handle conversion between native Java objects which represent
 * CEL values and well-known protobuf counterparts.
 *
 * <p>How a protobuf is adapted can depend on both the set of {@code Descriptor} values as well as
 * on feature-flags. Whereas in the past such conversions were performed as static method calls,
 * this class represents an evolving trend toward conditional conversion.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@CheckReturnValue
@Internal
public final class ProtoAdapter {

  /**
   * Int converter handles bidirectional conversions between int32 <-> int64 values.
   *
   * <p>Note: Unlike the {@code RuntimeHelpers#INT32_TO_INT64} and {@code
   * RuntimeHelpers#INT64_TO_INT32} converter objects, the converter takes a {@code Number} as input
   * and produces a {@code Number} as output in an effort to make the coverters more tolerant of
   * Java native values which may enter the evaluation via {@code DYN} inputs.
   */
  public static final BidiConverter<Number, Number> INT_CONVERTER =
      BidiConverter.of(Number::longValue, value -> intCheckedCast(value.longValue()));

  /**
   * Signed uint converter handles bidirectional conversions between uint32 <-> uint64 values.
   *
   * <p>Note: Unlike the {@code RuntimeHelpers#UINT32_TO_UINT64} and {@code
   * RuntimeHelpers#UINT64_TO_UINT32} converter objects, the converter takes a {@code Number} as
   * input and produces a {@code Number} as output in an effort to make the coverters more tolerant
   * of Java native values which may enter the evaluation via {@code DYN} inputs.
   *
   * <p>If the long being converted to a uint exceeds 32 bits, an {@code IllegalArgumentException}
   * will be thrown.
   */
  public static final BidiConverter<Number, Number> SIGNED_UINT32_CONVERTER =
      BidiConverter.of(
          value -> UnsignedInts.toLong(value.intValue()),
          value -> unsignedIntCheckedCast(value.longValue()));

  /**
   * Unigned uint converter handles bidirectional conversions between uint32 <-> uint64 values.
   *
   * <p>Note: Unlike the {@code RuntimeHelpers#UINT32_TO_UINT64} and {@code
   * RuntimeHelpers#UINT64_TO_UINT32} converter objects, the converter takes a {@code Number} as
   * input and produces a {@code Number} as output in an effort to make the coverters more tolerant
   * of Java native values which may enter the evaluation via {@code DYN} inputs.
   *
   * <p>If the long being converted to a uint exceeds 32 bits, an {@code IllegalArgumentException}
   * will be thrown.
   */
  public static final BidiConverter<Number, Number> UNSIGNED_UINT32_CONVERTER =
      BidiConverter.of(
          value -> UnsignedLong.fromLongBits(Integer.toUnsignedLong(value.intValue())),
          value -> unsignedIntCheckedCast(value.longValue()));

  /**
   * Unsigned uint64 converter which adapts from a {@code long} value on the wire to an
   * {@code UnsignedLong}.
   */
  public static final BidiConverter<Number, Number> UNSIGNED_UINT64_CONVERTER =
      BidiConverter.of(value -> UnsignedLong.fromLongBits(value.longValue()), Number::longValue);

  /**
   * Double converter handles bidirectional conversions between float32 <-> float64 values.
   *
   * <p>Note: Unlike the {@code RuntimeHelpers#FLOAT_TO_DOUBLE} and {@code
   * RuntimeHelpers#DOUBLE_TO_FLOAT} converter objects, the converter takes a {@code Number} as
   * input and produces a {@code Number} as output in an effort to make the coverters more tolerant
   * of Java native values which may enter the evaluation via {@code DYN} inputs.
   */
  public static final BidiConverter<Number, Number> DOUBLE_CONVERTER =
      BidiConverter.of(Number::doubleValue, Number::floatValue);

  private final DynamicProto dynamicProto;
  private final boolean enableUnsignedLongs;

  public ProtoAdapter(DynamicProto dynamicProto, boolean enableUnsignedLongs) {
    this.dynamicProto = checkNotNull(dynamicProto);
    this.enableUnsignedLongs = enableUnsignedLongs;
  }

  /**
   * Adapts a protobuf {@code MessageOrBuilder} into a Java object understood by CEL.
   *
   * <p>In some instances the input message is also the output value.
   */
  public Object adaptProtoToValue(MessageOrBuilder proto) {
    // Attempt to convert the proto from a dynamic message into a linked protobuf type if possible.
    if (proto instanceof DynamicMessage) {
      proto = dynamicProto.maybeAdaptDynamicMessage((DynamicMessage) proto);
    }
    // If the proto is not a well-known type, then the input Message is what's expected as the
    // output return value.
    WellKnownProto wellKnownProto =
        WellKnownProto.getByDescriptorName(typeName(proto.getDescriptorForType()));
    if (wellKnownProto == null) {
      return proto;
    }
    // Exhaustive switch over the conversion and adaptation of well-known protobuf types to Java
    // values.
    switch (wellKnownProto) {
      case ANY_VALUE:
        return unpackAnyProto((Any) proto);
      case JSON_VALUE:
        return adaptJsonToValue((Value) proto);
      case JSON_STRUCT_VALUE:
        return adaptJsonStructToValue((Struct) proto);
      case JSON_LIST_VALUE:
        return adaptJsonListToValue((ListValue) proto);
      case BOOL_VALUE:
        return ((BoolValue) proto).getValue();
      case BYTES_VALUE:
        return ((BytesValue) proto).getValue();
      case DOUBLE_VALUE:
        return ((DoubleValue) proto).getValue();
      case FLOAT_VALUE:
        return (double) ((FloatValue) proto).getValue();
      case INT32_VALUE:
        return (long) ((Int32Value) proto).getValue();
      case INT64_VALUE:
        return ((Int64Value) proto).getValue();
      case STRING_VALUE:
        return ((StringValue) proto).getValue();
      case UINT32_VALUE:
        if (enableUnsignedLongs) {
          return UnsignedLong.fromLongBits(
              Integer.toUnsignedLong(((UInt32Value) proto).getValue()));
        }
        return (long) ((UInt32Value) proto).getValue();
      case UINT64_VALUE:
        if (enableUnsignedLongs) {
          return UnsignedLong.fromLongBits(((UInt64Value) proto).getValue());
        }
        return ((UInt64Value) proto).getValue();
      default:
        return proto;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Optional<Object> adaptFieldToValue(FieldDescriptor fieldDescriptor, Object fieldValue) {
    if (isUnknown(fieldValue)) {
      return Optional.of(fieldValue);
    }
    if (fieldDescriptor.isMapField()) {
      Descriptor entryDescriptor = fieldDescriptor.getMessageType();
      FieldDescriptor keyFieldDescriptor = entryDescriptor.findFieldByNumber(1);
      FieldDescriptor valueFieldDescriptor = entryDescriptor.findFieldByNumber(2);
      BidiConverter keyConverter = fieldToValueConverter(keyFieldDescriptor);
      BidiConverter valueConverter = fieldToValueConverter(valueFieldDescriptor);

      Map<Object, Object> map = new HashMap<>();
      Object mapKey;
      Object mapValue;
      for (Object entry : ((List<Object>) fieldValue)) {
        if (entry instanceof MapEntry) {
          MapEntry mapEntry = (MapEntry) entry;
          mapKey = mapEntry.getKey();
          mapValue = mapEntry.getValue();
        } else if (entry instanceof DynamicMessage) {
          DynamicMessage dynamicMessage = (DynamicMessage) entry;
          mapKey = dynamicMessage.getField(keyFieldDescriptor);
          mapValue = dynamicMessage.getField(valueFieldDescriptor);
        } else {
          throw new IllegalStateException("Unexpected map field type: " + entry);
        }

        map.put(
            keyConverter.forwardConverter().convert(mapKey),
            valueConverter.forwardConverter().convert(mapValue));
      }

      return Optional.of(map);
    }
    if (fieldDescriptor.isRepeated()) {
      BidiConverter bidiConverter = fieldToValueConverter(fieldDescriptor);
      if (bidiConverter == BidiConverter.IDENTITY) {
        return Optional.of(fieldValue);
      }
      return Optional.of(AdaptingTypes.adaptingList((List<?>) fieldValue, bidiConverter));
    }
    return Optional.of(
        fieldToValueConverter(fieldDescriptor).forwardConverter().convert(fieldValue));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Optional<Object> adaptValueToFieldType(
      FieldDescriptor fieldDescriptor, Object fieldValue) {
    if (isWrapperType(fieldDescriptor) && fieldValue.equals(NullValue.NULL_VALUE)) {
      return Optional.empty();
    }
    if (isUnknown(fieldValue)) {
      return Optional.of(fieldValue);
    }
    if (fieldDescriptor.isMapField()) {
      Descriptor entryDescriptor = fieldDescriptor.getMessageType();
      FieldDescriptor keyDescriptor = entryDescriptor.findFieldByNumber(1);
      FieldDescriptor valueDescriptor = entryDescriptor.findFieldByNumber(2);
      BidiConverter keyConverter = fieldToValueConverter(keyDescriptor);
      BidiConverter valueConverter = fieldToValueConverter(valueDescriptor);
      List<MapEntry> mapEntries = new ArrayList<>();
      MapEntry protoMapEntry =
          MapEntry.newDefaultInstance(
              entryDescriptor,
              keyDescriptor.getLiteType(),
              getDefaultValueForMaybeMessage(keyDescriptor),
              valueDescriptor.getLiteType(),
              getDefaultValueForMaybeMessage(valueDescriptor));
      for (Map.Entry entry : ((Map<?, ?>) fieldValue).entrySet()) {
        mapEntries.add(
            protoMapEntry.toBuilder()
                .setKey(keyConverter.backwardConverter().convert(entry.getKey()))
                .setValue(valueConverter.backwardConverter().convert(entry.getValue()))
                .build());
      }
      return Optional.of(mapEntries);
    }
    if (fieldDescriptor.isRepeated()) {
      return Optional.of(
          AdaptingTypes.adaptingList(
              (List<?>) fieldValue, fieldToValueConverter(fieldDescriptor).reverse()));
    }
    return Optional.of(
        fieldToValueConverter(fieldDescriptor).backwardConverter().convert(fieldValue));
  }

  @SuppressWarnings("rawtypes")
  private BidiConverter fieldToValueConverter(FieldDescriptor fieldDescriptor) {
    switch (fieldDescriptor.getType()) {
      case SFIXED32:
      case SINT32:
      case INT32:
        return INT_CONVERTER;
      case FIXED32:
      case UINT32:
        if (enableUnsignedLongs) {
          return UNSIGNED_UINT32_CONVERTER;
        }
        return SIGNED_UINT32_CONVERTER;
      case FIXED64:
      case UINT64:
        if (enableUnsignedLongs) {
          return UNSIGNED_UINT64_CONVERTER;
        }
        return BidiConverter.IDENTITY;
      case FLOAT:
        return DOUBLE_CONVERTER;
      case ENUM:
        return BidiConverter.<Object, Long>of(
            value -> (long) ((EnumValueDescriptor) value).getNumber(),
            number ->
                fieldDescriptor
                    .getEnumType()
                    .findValueByNumberCreatingIfUnknown(number.intValue()));
      case MESSAGE:
        return BidiConverter.<MessageOrBuilder, Object>of(
            this::adaptProtoToValue,
            value ->
                adaptValueToProto(value, fieldDescriptor.getMessageType().getFullName())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format("value not convertible to proto: %s", value))));
      default:
        return BidiConverter.IDENTITY;
    }
  }

  /**
   * Adapt the Java object {@code value} to the given protobuf {@code protoTypeName} if possible.
   *
   * <p>If the Java value can be represented as a proto {@code Message}, then a conversion will be
   * performed. In some cases, the input {@code value} will be a {@code Message}, but the
   * {@code protoTypeName} will indicate an alternative packaging of the value which needs to be
   * considered, such as a packing an {@code google.protobuf.StringValue} into a {@code Any} value.
   */
  @SuppressWarnings("unchecked")
  public Optional<Message> adaptValueToProto(Object value, String protoTypeName) {
    WellKnownProto wellKnownProto = WellKnownProto.getByDescriptorName(protoTypeName);
    if (wellKnownProto == null) {
      if (value instanceof Message) {
        return Optional.of((Message) value);
      }
      return Optional.empty();
    }
    switch (wellKnownProto) {
      case ANY_VALUE:
        return Optional.ofNullable(adaptValueToAny(value));
      case JSON_VALUE:
        try {
          return Optional.of(CelProtoJsonAdapter.adaptValueToJsonValue(value));
        } catch (RuntimeException e) {
          return Optional.empty();
        }
      case JSON_LIST_VALUE:
        try {
          return Optional.of(CelProtoJsonAdapter.adaptToJsonListValue((Iterable<Object>) value));
        } catch (RuntimeException e) {
          return Optional.empty();
        }
      case JSON_STRUCT_VALUE:
        try {
          return Optional.of(
              CelProtoJsonAdapter.adaptToJsonStructValue((Map<String, Object>) value));
        } catch (RuntimeException e) {
          return Optional.empty();
        }
      case BOOL_VALUE:
        if (value instanceof Boolean) {
          return Optional.of(BoolValue.of((Boolean) value));
        }
        break;
      case BYTES_VALUE:
        if (value instanceof ByteString) {
          return Optional.of(BytesValue.of((ByteString) value));
        }
        break;
      case DOUBLE_VALUE:
        return Optional.ofNullable(adaptValueToDouble(value));
      case DURATION_VALUE:
        return Optional.of((Duration) value);
      case FLOAT_VALUE:
        return Optional.ofNullable(adaptValueToFloat(value));
      case INT32_VALUE:
        return Optional.ofNullable(adaptValueToInt32(value));
      case INT64_VALUE:
        return Optional.ofNullable(adaptValueToInt64(value));
      case STRING_VALUE:
        if (value instanceof String) {
          return Optional.of(StringValue.of((String) value));
        }
        break;
      case TIMESTAMP_VALUE:
        return Optional.of((Timestamp) value);
      case UINT32_VALUE:
        return Optional.ofNullable(adaptValueToUint32(value));
      case UINT64_VALUE:
        return Optional.ofNullable(adaptValueToUint64(value));
    }
    return Optional.empty();
  }

  // Helper functions which return a {@code null} value if the conversion is not successful.
  // This technique was chosen over {@code Optional} for brevity as any call site which might
  // care about an Optional return is handled higher up the call stack.

  private @Nullable Message adaptValueToAny(Object value) {
    if (value == null || value instanceof NullValue) {
      return Any.pack(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    }
    if (value instanceof Boolean) {
      return maybePackAny(value, WellKnownProto.BOOL_VALUE);
    }
    if (value instanceof ByteString) {
      return maybePackAny(value, WellKnownProto.BYTES_VALUE);
    }
    if (value instanceof Double) {
      return maybePackAny(value, WellKnownProto.DOUBLE_VALUE);
    }
    if (value instanceof Float) {
      return maybePackAny(value, WellKnownProto.FLOAT_VALUE);
    }
    if (value instanceof Integer) {
      return maybePackAny(value, WellKnownProto.INT32_VALUE);
    }
    if (value instanceof Long) {
      return maybePackAny(value, WellKnownProto.INT64_VALUE);
    }
    if (value instanceof Message) {
      return Any.pack((Message) value);
    }
    if (value instanceof Iterable) {
      return maybePackAny(value, WellKnownProto.JSON_LIST_VALUE);
    }
    if (value instanceof Map) {
      return maybePackAny(value, WellKnownProto.JSON_STRUCT_VALUE);
    }
    if (value instanceof String) {
      return maybePackAny(value, WellKnownProto.STRING_VALUE);
    }
    if (value instanceof UnsignedLong) {
      return maybePackAny(value, WellKnownProto.UINT64_VALUE);
    }
    return null;
  }

  private @Nullable Any maybePackAny(Object value, WellKnownProto wellKnownProto) {
    Optional<Message> protoValue = adaptValueToProto(value, wellKnownProto.typeName());
    return protoValue.map(Any::pack).orElse(null);
  }

  private @Nullable Message adaptValueToDouble(Object value) {
    if (value instanceof Double) {
      return DoubleValue.of((Double) value);
    }
    if (value instanceof Float) {
      return DoubleValue.of(((Float) value).doubleValue());
    }
    return null;
  }

  private @Nullable Message adaptValueToFloat(Object value) {
    if (value instanceof Double) {
      return FloatValue.of(((Double) value).floatValue());
    }
    if (value instanceof Float) {
      return FloatValue.of((Float) value);
    }
    return null;
  }

  private @Nullable Message adaptValueToInt32(Object value) {
    if (value instanceof Integer) {
      return Int32Value.of((Integer) value);
    }
    if (value instanceof Long) {
      return Int32Value.of(intCheckedCast((Long) value));
    }
    return null;
  }

  private @Nullable Message adaptValueToInt64(Object value) {
    if (value instanceof Integer) {
      return Int64Value.of(((Integer) value).longValue());
    }
    if (value instanceof Long) {
      return Int64Value.of((Long) value);
    }
    return null;
  }

  private @Nullable Message adaptValueToUint32(Object value) {
    if (value instanceof Integer) {
      return UInt32Value.of((Integer) value);
    }
    if (value instanceof Long) {
      try {
        return UInt32Value.of(unsignedIntCheckedCast((Long) value));
      } catch (IllegalArgumentException e) {
        throw new CelRuntimeException(e, CelErrorCode.NUMERIC_OVERFLOW);
      }
    }
    if (value instanceof UnsignedLong) {
      try {
        return UInt32Value.of(unsignedIntCheckedCast(((UnsignedLong) value).longValue()));
      } catch (IllegalArgumentException e) {
        throw new CelRuntimeException(e, CelErrorCode.NUMERIC_OVERFLOW);
      }
    }
    return null;
  }

  private @Nullable Message adaptValueToUint64(Object value) {
    if (value instanceof Integer) {
      return UInt64Value.of(UnsignedInts.toLong((Integer) value));
    }
    if (value instanceof Long) {
      return UInt64Value.of((Long) value);
    }
    if (value instanceof UnsignedLong) {
      return UInt64Value.of(((UnsignedLong) value).longValue());
    }
    return null;
  }

  private @Nullable Object adaptJsonToValue(Value value) {
    switch (value.getKindCase()) {
      case BOOL_VALUE:
        return value.getBoolValue();
      case NULL_VALUE:
        return value.getNullValue();
      case NUMBER_VALUE:
        return value.getNumberValue();
      case STRING_VALUE:
        return value.getStringValue();
      case LIST_VALUE:
        return adaptJsonListToValue(value.getListValue());
      case STRUCT_VALUE:
        return adaptJsonStructToValue(value.getStructValue());
      case KIND_NOT_SET:
        return NullValue.NULL_VALUE;
    }
    return null;
  }

  private Object unpackAnyProto(Any anyProto) {
    try {
      return adaptProtoToValue(dynamicProto.unpack(anyProto));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private ImmutableList<Object> adaptJsonListToValue(ListValue listValue) {
    return listValue.getValuesList().stream()
        .map(this::adaptJsonToValue)
        .collect(ImmutableList.<Object>toImmutableList());
  }

  private ImmutableMap<String, Object> adaptJsonStructToValue(Struct struct) {
    return struct.getFieldsMap().entrySet().stream()
        .collect(toImmutableMap(e -> e.getKey(), e -> adaptJsonToValue(e.getValue())));
  }

  /**
   * Returns the default value for a field that can be a proto message
   */
  private static Object getDefaultValueForMaybeMessage(FieldDescriptor descriptor) {
    if (descriptor.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
      return DynamicMessage.getDefaultInstance(descriptor.getMessageType());
    }

    return descriptor.getDefaultValue();
  }

  private static String typeName(Descriptor protoType) {
    return protoType.getFullName();
  }

  private static boolean isWrapperType(FieldDescriptor fieldDescriptor) {
    if (fieldDescriptor.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
      return false;
    }
    String fieldTypeName = fieldDescriptor.getMessageType().getFullName();
    WellKnownProto wellKnownProto = WellKnownProto.getByDescriptorName(fieldTypeName);
    return wellKnownProto != null && wellKnownProto.isWrapperType();
  }

  private static boolean isUnknown(Object object) {
    return object instanceof ExprValue
        && ((ExprValue) object).getKindCase() == ExprValue.KindCase.UNKNOWN;
  }

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
}
