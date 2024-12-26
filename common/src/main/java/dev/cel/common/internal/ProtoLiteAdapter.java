package dev.cel.common.internal;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import dev.cel.common.annotations.Internal;
import java.util.Map.Entry;

@Internal
@Immutable
public final class ProtoLiteAdapter {

  private final boolean enableUnsignedLongs;

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

  public ProtoLiteAdapter(boolean enableUnsignedLongs) {
    this.enableUnsignedLongs = enableUnsignedLongs;
  }
}
