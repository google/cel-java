package dev.cel.common.internal;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
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
import java.util.Map;
import java.util.Map.Entry;

@Internal
@Immutable
public final class ProtoLiteAdapter {

  private final boolean enableUnsignedLongs;

  public MessageLite adaptValueToWellKnownProto(Object value, WellKnownProto wellKnownProto) {
    switch (wellKnownProto) {
      case ANY_VALUE:
        break;
      case JSON_VALUE:
        return CelProtoJsonAdapter.adaptValueToJsonValue(value);
      case JSON_STRUCT_VALUE:
        return CelProtoJsonAdapter.adaptToJsonStructValue((Map<String, Object>) value);
      case JSON_LIST_VALUE:
        return CelProtoJsonAdapter.adaptToJsonListValue((Iterable<Object>) value);
      case BOOL_VALUE:
        return BoolValue.of((Boolean) value);
      case BYTES_VALUE:
        return BytesValue.of((ByteString) value);
      case DOUBLE_VALUE:
        return adaptValueToDouble(value);
      case FLOAT_VALUE:
        return adaptValueToFloat(value);
      case INT32_VALUE:
        return adaptValueToInt32(value);
      case INT64_VALUE:
        return adaptValueToInt64(value);
      case STRING_VALUE:
        return StringValue.of((String) value);
      case UINT32_VALUE:
        return adaptValueToUint32(value);
      case UINT64_VALUE:
        return adaptValueToUint64(value);
      case DURATION_VALUE:
        return (Duration) value;
      case TIMESTAMP_VALUE:
        return (Timestamp) value;
      default:
        throw new IllegalArgumentException("Unexpceted wellKnownProto kind: " + wellKnownProto);
    }
    return null;
  }


  public Object adaptWellKnownProtoToValue(MessageLiteOrBuilder proto, WellKnownProto wellKnownProto) {
    // Exhaustive switch over the conversion and adaptation of well-known protobuf types to Java
    // values.
    switch (wellKnownProto) {
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

  private Object adaptJsonToValue(Value value) {
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
      default:
        throw new IllegalArgumentException("unexpected value kind: " + value.getKindCase());
    }
  }

  private ImmutableList<Object> adaptJsonListToValue(ListValue listValue) {
    return listValue.getValuesList().stream()
        .map(this::adaptJsonToValue)
        .collect(ImmutableList.<Object>toImmutableList());
  }

  private ImmutableMap<String, Object> adaptJsonStructToValue(Struct struct) {
    return struct.getFieldsMap().entrySet().stream()
        .collect(toImmutableMap(Entry::getKey, e -> adaptJsonToValue(e.getValue())));
  }

  private Message adaptValueToDouble(Object value) {
    if (value instanceof Double) {
      return DoubleValue.of((Double) value);
    }
    if (value instanceof Float) {
      return DoubleValue.of(((Float) value).doubleValue());
    }
    throw new IllegalArgumentException("Unexpected value type: " + value);
  }

  private Message adaptValueToFloat(Object value) {
    if (value instanceof Double) {
      return FloatValue.of(((Double) value).floatValue());
    }
    if (value instanceof Float) {
      return FloatValue.of((Float) value);
    }
    throw new IllegalArgumentException("Unexpected value type: " + value);
  }

  private Message adaptValueToInt32(Object value) {
    if (value instanceof Integer) {
      return Int32Value.of((Integer) value);
    }
    if (value instanceof Long) {
      return Int32Value.of(intCheckedCast((Long) value));
    }
    throw new IllegalArgumentException("Unexpected value type: " + value);
  }

  private Message adaptValueToInt64(Object value) {
    if (value instanceof Integer) {
      return Int64Value.of(((Integer) value).longValue());
    }
    if (value instanceof Long) {
      return Int64Value.of((Long) value);
    }

    throw new IllegalArgumentException("Unexpected value type: " + value);
  }

  private Message adaptValueToUint32(Object value) {
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

    throw new IllegalArgumentException("Unexpected value type: " + value);
  }

  private Message adaptValueToUint64(Object value) {
    if (value instanceof Integer) {
      return UInt64Value.of(UnsignedInts.toLong((Integer) value));
    }
    if (value instanceof Long) {
      return UInt64Value.of((Long) value);
    }
    if (value instanceof UnsignedLong) {
      return UInt64Value.of(((UnsignedLong) value).longValue());
    }

    throw new IllegalArgumentException("Unexpected value type: " + value);
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

  public ProtoLiteAdapter(boolean enableUnsignedLongs) {
    this.enableUnsignedLongs = enableUnsignedLongs;
  }
}
