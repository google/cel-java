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

package dev.cel.common.values;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.math.LongMath.checkedAdd;
import static com.google.common.math.LongMath.checkedSubtract;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.WellKnownProto;
import java.time.Duration;
import java.time.Instant;

/**
 * {@code BaseProtoCelValueConverter} contains the common logic for converting between native Java
 * and protobuf objects to {@link CelValue}. This base class is inherited by {@code
 * ProtoCelValueConverter} and {@code ProtoLiteCelValueConverter} to perform the conversion using
 * full and lite variants of protobuf messages respectively.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public abstract class BaseProtoCelValueConverter extends CelValueConverter {

  /**
   * Adapts a {@link CelValue} to a native Java object. The CelValue is adapted into protobuf object
   * when an equivalent exists.
   */
  @Override
  public Object fromCelValueToJavaObject(CelValue celValue) {
    Preconditions.checkNotNull(celValue);

    if (celValue instanceof TimestampValue) {
      return TimeUtils.toProtoTimestamp(((TimestampValue) celValue).value());
    } else if (celValue instanceof DurationValue) {
      return TimeUtils.toProtoDuration(((DurationValue) celValue).value());
    } else if (celValue instanceof BytesValue) {
      return ByteString.copyFrom(((BytesValue) celValue).value().toByteArray());
    } else if (celValue.equals(NullValue.NULL_VALUE)) {
      return com.google.protobuf.NullValue.NULL_VALUE;
    }

    return super.fromCelValueToJavaObject(celValue);
  }

  /**
   * Adapts a plain old Java Object to a {@link CelValue}. Protobuf semantics take precedence for
   * conversion.
   */
  @Override
  public CelValue fromJavaObjectToCelValue(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof ByteString) {
      return BytesValue.create(CelByteString.of(((ByteString) value).toByteArray()));
    } else if (value instanceof com.google.protobuf.NullValue) {
      return NullValue.NULL_VALUE;
    }

    return super.fromJavaObjectToCelValue(value);
  }

  protected final CelValue fromWellKnownProtoToCelValue(
      MessageLiteOrBuilder message, WellKnownProto wellKnownProto) {
    switch (wellKnownProto) {
      case JSON_VALUE:
        return adaptJsonValueToCelValue((Value) message);
      case JSON_STRUCT_VALUE:
        return adaptJsonStructToCelValue((Struct) message);
      case JSON_LIST_VALUE:
        return adaptJsonListToCelValue((com.google.protobuf.ListValue) message);
      case DURATION:
        return DurationValue.create(
            TimeUtils.toJavaDuration((com.google.protobuf.Duration) message));
      case TIMESTAMP:
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
            ((UInt32Value) message).getValue(), true);
      case UINT64_VALUE:
        return UintValue.create(
            ((UInt64Value) message).getValue(), true);
      default:
        throw new UnsupportedOperationException(
            "Unsupported message to CelValue conversion - " + message);
    }
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

  protected BaseProtoCelValueConverter() {}
}
