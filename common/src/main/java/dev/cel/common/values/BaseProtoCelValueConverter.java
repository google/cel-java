package dev.cel.common.values;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.math.LongMath.checkedAdd;
import static com.google.common.math.LongMath.checkedSubtract;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import dev.cel.common.CelOptions;
import java.time.Duration;
import java.time.Instant;

@Immutable
abstract class BaseProtoCelValueConverter extends CelValueConverter {

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
    } else if (NullValue.NULL_VALUE.equals(celValue)) {
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

  protected BaseProtoCelValueConverter(CelOptions celOptions) {
    super(celOptions);
  }
}
