// Copyright 2023 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.math.LongMath.checkedAdd;
import static com.google.common.math.LongMath.checkedSubtract;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.common.types.CelTypes;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ProtoCelValueConverter} handles bidirectional conversion between native Java and protobuf
 * objects to {@link CelValue}. This converter leverages descriptors, thus requires the full version of protobuf implementation.
 *
 * <p>Protobuf semantics take precedence for conversion. For example, CEL's TimestampValue will be
 * converted into Protobuf's Timestamp instead of java.time.Instant.
 *
 *
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class ProtoCelValueConverter extends BaseProtoCelValueConverter {
  private final CelDescriptorPool celDescriptorPool;
  private final DynamicProto dynamicProto;

  /** Constructs a new instance of ProtoCelValueConverter. */
  public static ProtoCelValueConverter newInstance(
      CelOptions celOptions, CelDescriptorPool celDescriptorPool, DynamicProto dynamicProto) {
    return new ProtoCelValueConverter(celOptions, celDescriptorPool, dynamicProto);
  }

  /** Adapts a Protobuf message into a {@link CelValue}. */
  public CelValue fromProtoMessageToCelValue(MessageOrBuilder message) {
    Preconditions.checkNotNull(message);

    // Attempt to convert the proto from a dynamic message into a concrete message if possible.
    if (message instanceof DynamicMessage) {
      message = dynamicProto.maybeAdaptDynamicMessage((DynamicMessage) message);
    }

    WellKnownProto wellKnownProto =
        WellKnownProto.getByTypeName(message.getDescriptorForType().getFullName());
    if (wellKnownProto == null) {
      return ProtoMessageValue.create((Message) message, celDescriptorPool, this);
    }

    switch (wellKnownProto) {
      case ANY_VALUE:
        Message unpackedMessage;
        try {
          unpackedMessage = dynamicProto.unpack((Any) message);
        } catch (InvalidProtocolBufferException e) {
          throw new IllegalStateException(
              "Unpacking failed for message: " + message.getDescriptorForType().getFullName(), e);
        }
        return fromProtoMessageToCelValue(unpackedMessage);
      case JSON_VALUE:
        return adaptJsonValueToCelValue((Value) message);
      case JSON_STRUCT_VALUE:
        return adaptJsonStructToCelValue((Struct) message);
      case JSON_LIST_VALUE:
        return adaptJsonListToCelValue((com.google.protobuf.ListValue) message);
      case DURATION_VALUE:
        return DurationValue.create(
            TimeUtils.toJavaDuration((com.google.protobuf.Duration) message));
      case TIMESTAMP_VALUE:
        return TimestampValue.create(TimeUtils.toJavaInstant((Timestamp) message));
      case BOOL_VALUE:
        return fromJavaPrimitiveToCelValue(((BoolValue) message).getValue());
      case BYTES_VALUE:
        return fromJavaPrimitiveToCelValue(
            ((com.google.protobuf.BytesValue) message).getValue().toByteArray());
      case DOUBLE_VALUE:
        return fromJavaPrimitiveToCelValue(((DoubleValue) message).getValue());
      case FLOAT_VALUE:
        return fromJavaPrimitiveToCelValue(((FloatValue) message).getValue());
      case INT32_VALUE:
        return fromJavaPrimitiveToCelValue(((Int32Value) message).getValue());
      case INT64_VALUE:
        return fromJavaPrimitiveToCelValue(((Int64Value) message).getValue());
      case STRING_VALUE:
        return fromJavaPrimitiveToCelValue(((StringValue) message).getValue());
      case UINT32_VALUE:
        return UintValue.create(
            ((UInt32Value) message).getValue(), celOptions.enableUnsignedLongs());
      case UINT64_VALUE:
        return UintValue.create(
            ((UInt64Value) message).getValue(), celOptions.enableUnsignedLongs());
    }

    throw new UnsupportedOperationException(
        "Unsupported message to CelValue conversion - " + message);
  }

  /**
   * Adapts a plain old Java Object to a {@link CelValue}. Protobuf semantics take precedence for
   * conversion.
   */
  @Override
  public CelValue fromJavaObjectToCelValue(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof Message) {
      return fromProtoMessageToCelValue((Message) value);
    } else if (value instanceof Message.Builder) {
      Message.Builder msgBuilder = (Message.Builder) value;
      return fromProtoMessageToCelValue(msgBuilder.build());
    } else if (value instanceof EnumValueDescriptor) {
      // (b/178627883) Strongly typed enum is not supported yet
      return IntValue.create(((EnumValueDescriptor) value).getNumber());
    }

    return super.fromJavaObjectToCelValue(value);
  }

  /** Adapts the protobuf message field into {@link CelValue}. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public CelValue fromProtoMessageFieldToCelValue(
      Message message, FieldDescriptor fieldDescriptor) {
    Preconditions.checkNotNull(message);
    Preconditions.checkNotNull(fieldDescriptor);

    Object result = message.getField(fieldDescriptor);
    switch (fieldDescriptor.getType()) {
      case MESSAGE:
        if (CelTypes.isWrapperType(fieldDescriptor.getMessageType().getFullName())
            && !message.hasField(fieldDescriptor)) {
          // Special semantics for wrapper types per CEL specification. These all convert into null
          // instead of the default value.
          return NullValue.NULL_VALUE;
        } else if (fieldDescriptor.isMapField()) {
          Map<Object, Object> map = new HashMap<>();
          Object mapKey;
          Object mapValue;
          for (Object entry : ((List<Object>) result)) {
            if (entry instanceof MapEntry) {
              MapEntry mapEntry = (MapEntry) entry;
              mapKey = mapEntry.getKey();
              mapValue = mapEntry.getValue();
            } else if (entry instanceof DynamicMessage) {
              DynamicMessage dynamicMessage = (DynamicMessage) entry;
              FieldDescriptor keyFieldDescriptor =
                  fieldDescriptor.getMessageType().findFieldByNumber(1);
              FieldDescriptor valueFieldDescriptor =
                  fieldDescriptor.getMessageType().findFieldByNumber(2);
              mapKey = dynamicMessage.getField(keyFieldDescriptor);
              mapValue = dynamicMessage.getField(valueFieldDescriptor);
            } else {
              throw new IllegalStateException("Unexpected map field type: " + entry);
            }

            map.put(mapKey, mapValue);
          }
          return fromJavaObjectToCelValue(map);
        }
        break;
      case UINT32:
        return UintValue.create((int) result, celOptions.enableUnsignedLongs());
      case UINT64:
        return UintValue.create((long) result, celOptions.enableUnsignedLongs());
      default:
        break;
    }

    return fromJavaObjectToCelValue(result);
  }

  private CelValue adaptJsonValueToCelValue(Value value) {
    switch (value.getKindCase()) {
      case BOOL_VALUE:
        return fromJavaPrimitiveToCelValue(value.getBoolValue());
      case NUMBER_VALUE:
        return fromJavaPrimitiveToCelValue(value.getNumberValue());
      case STRING_VALUE:
        return fromJavaPrimitiveToCelValue(value.getStringValue());
      case LIST_VALUE:
        return adaptJsonListToCelValue(value.getListValue());
      case STRUCT_VALUE:
        return adaptJsonStructToCelValue(value.getStructValue());
      case NULL_VALUE:
      case KIND_NOT_SET: // Fall-through is intended
        return NullValue.NULL_VALUE;
    }
    throw new UnsupportedOperationException(
        "Unsupported Json to CelValue conversion: " + value.getKindCase());
  }

  private ListValue<CelValue> adaptJsonListToCelValue(com.google.protobuf.ListValue listValue) {
    return ImmutableListValue.create(
        listValue.getValuesList().stream()
            .map(this::adaptJsonValueToCelValue)
            .collect(toImmutableList()));
  }

  private MapValue<CelValue, CelValue> adaptJsonStructToCelValue(Struct struct) {
    return ImmutableMapValue.create(
        struct.getFieldsMap().entrySet().stream()
            .collect(
                toImmutableMap(
                    e -> fromJavaObjectToCelValue(e.getKey()),
                    e -> adaptJsonValueToCelValue(e.getValue()))));
  }

  /** Helper to convert between java.util.time and protobuf duration/timestamp. */
  private static class TimeUtils {
    private static final int NANOS_PER_SECOND = 1000000000;

    private static Instant toJavaInstant(Timestamp timestamp) {
      timestamp = normalizedTimestamp(timestamp.getSeconds(), timestamp.getNanos());
      return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Duration toJavaDuration(com.google.protobuf.Duration duration) {
      duration = normalizedDuration(duration.getSeconds(), duration.getNanos());
      return java.time.Duration.ofSeconds(duration.getSeconds(), duration.getNanos());
    }

    private static Timestamp toProtoTimestamp(Instant instant) {
      return normalizedTimestamp(instant.getEpochSecond(), instant.getNano());
    }

    private static com.google.protobuf.Duration toProtoDuration(Duration duration) {
      return normalizedDuration(duration.getSeconds(), duration.getNano());
    }

    private static Timestamp normalizedTimestamp(long seconds, int nanos) {
      if (nanos <= -NANOS_PER_SECOND || nanos >= NANOS_PER_SECOND) {
        seconds = checkedAdd(seconds, nanos / NANOS_PER_SECOND);
        nanos = nanos % NANOS_PER_SECOND;
      }
      if (nanos < 0) {
        nanos = nanos + NANOS_PER_SECOND; // no overflow since nanos is negative (and we're adding)
        seconds = checkedSubtract(seconds, 1);
      }
      Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
      return Timestamps.checkValid(timestamp);
    }

    private static com.google.protobuf.Duration normalizedDuration(long seconds, int nanos) {
      if (nanos <= -NANOS_PER_SECOND || nanos >= NANOS_PER_SECOND) {
        seconds = checkedAdd(seconds, nanos / NANOS_PER_SECOND);
        nanos %= NANOS_PER_SECOND;
      }
      if (seconds > 0 && nanos < 0) {
        nanos += NANOS_PER_SECOND; // no overflow since nanos is negative (and we're adding)
        seconds--; // no overflow since seconds is positive (and we're decrementing)
      }
      if (seconds < 0 && nanos > 0) {
        nanos -= NANOS_PER_SECOND; // no overflow since nanos is positive (and we're subtracting)
        seconds++; // no overflow since seconds is negative (and we're incrementing)
      }
      com.google.protobuf.Duration duration =
          com.google.protobuf.Duration.newBuilder().setSeconds(seconds).setNanos(nanos).build();
      return Durations.checkValid(duration);
    }
  }

  private ProtoCelValueConverter(
      CelOptions celOptions, CelDescriptorPool celDescriptorPool, DynamicProto dynamicProto) {
    super(celOptions);
    Preconditions.checkNotNull(celDescriptorPool);
    Preconditions.checkNotNull(dynamicProto);
    this.celDescriptorPool = celDescriptorPool;
    this.dynamicProto = dynamicProto;
  }
}
