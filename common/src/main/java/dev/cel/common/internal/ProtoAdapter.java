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

import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
   * Unsigned uint64 converter which adapts from a {@code long} value on the wire to an {@code
   * UnsignedLong}.
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

  private final ProtoLiteAdapter protoLiteAdapter;
  private final DynamicProto dynamicProto;
  private final boolean enableUnsignedLongs;

  public ProtoAdapter(DynamicProto dynamicProto, boolean enableUnsignedLongs) {
    this.dynamicProto = checkNotNull(dynamicProto);
    this.enableUnsignedLongs = enableUnsignedLongs;
    this.protoLiteAdapter = new ProtoLiteAdapter(enableUnsignedLongs);
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
        WellKnownProto.getByTypeName(typeName(proto.getDescriptorForType())).orElse(null);
    if (wellKnownProto == null) {
      return proto;
    }
    // Exhaustive switch over the conversion and adaptation of well-known protobuf types to Java
    // values.
    switch (wellKnownProto) {
      case ANY_VALUE:
        return unpackAnyProto((Any) proto);
      default:
        return protoLiteAdapter.adaptWellKnownProtoToValue(proto, wellKnownProto);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Optional<Object> adaptFieldToValue(FieldDescriptor fieldDescriptor, Object fieldValue) {
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
            value -> adaptValueToProto(value, fieldDescriptor.getMessageType().getFullName()));
      default:
        return BidiConverter.IDENTITY;
    }
  }

  /**
   * Adapt the Java object {@code value} to the given protobuf {@code protoTypeName} if possible.
   *
   * <p>If the Java value can be represented as a proto {@code Message}, then a conversion will be
   * performed. In some cases, the input {@code value} will be a {@code Message}, but the {@code
   * protoTypeName} will indicate an alternative packaging of the value which needs to be
   * considered, such as a packing an {@code google.protobuf.StringValue} into a {@code Any} value.
   */
  public Message adaptValueToProto(Object value, String protoTypeName) {
    WellKnownProto wellKnownProto = WellKnownProto.getByTypeName(protoTypeName).orElse(null);
    if (wellKnownProto == null) {
      if (value instanceof Message) {
        return (Message) value;
      }

      throw new IllegalStateException(String.format("value not convertible to proto: %s", value));
    }

    switch (wellKnownProto) {
      case ANY_VALUE:
        if (value instanceof Message) {
          protoTypeName = ((Message) value).getDescriptorForType().getFullName();
        }
        return protoLiteAdapter.adaptValueToAny(value, protoTypeName);
      default:
        return (Message) protoLiteAdapter.adaptValueToWellKnownProto(value, wellKnownProto);
    }
  }

  private Object unpackAnyProto(Any anyProto) {
    try {
      return adaptProtoToValue(dynamicProto.unpack(anyProto));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /** Returns the default value for a field that can be a proto message */
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
    return WellKnownProto.isWrapperType(fieldTypeName);
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
