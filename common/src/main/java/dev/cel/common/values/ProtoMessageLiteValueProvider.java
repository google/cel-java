package dev.cel.common.values;

import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Internal;
import com.google.protobuf.MessageLite;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelProtoJsonAdapter;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.common.types.CelTypes;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Immutable
public class ProtoMessageLiteValueProvider implements CelValueProvider {
  private final CelLiteDescriptorPool descriptorPool;

  @Override
  public Optional<CelValue> newValue(String structType, Map<String, Object> fields) {
    MessageInfo messageInfo = descriptorPool.findMessageInfoByTypeName(structType).orElse(null);

    if (messageInfo == null) {
      return Optional.empty();
    }

    MessageLite msg = DefaultInstanceMessageFactory.getInstance()
        .getPrototype(messageInfo.getFullyQualifiedProtoName(), messageInfo.getFullyQualifiedProtoJavaClassName())
        .orElse(null);

    if (msg == null) {
      return Optional.empty();
    }

    MessageLite.Builder msgBuilder = msg.toBuilder();
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      FieldInfo fieldInfo = messageInfo.getFieldInfoMap().get(entry.getKey());
      Method setterMethod;
      try {
        setterMethod = msgBuilder.getClass().getMethod(fieldInfo.getSetterName(), fieldInfo.getFieldJavaClass());
      } catch (NoSuchMethodException e) {
        throw new LinkageError(
            String.format("setter method %s does not exist in class: %s.", fieldInfo.getSetterName(), messageInfo.getFullyQualifiedProtoName()),
            e);
      }

      Object newFieldValue = adaptToProtoFieldCompatibleValue(entry.getValue(), fieldInfo, setterMethod.getParameters()[0]);
      try {
        setterMethod.invoke(msgBuilder, newFieldValue);
      } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
        throw new LinkageError(
            String.format("setter method %s invocation failed for class: %s.", fieldInfo.getSetterName(), messageInfo.getFullyQualifiedProtoName()),
            e);
      }
    }

    return Optional.of(ProtoMessageLiteValue.create(msgBuilder.build(), messageInfo.getFullyQualifiedProtoName()));
  }

  private static Object adaptToProtoFieldCompatibleValue(Object value, FieldInfo fieldInfo, Parameter parameter) {
    Class<?> parameterType = parameter.getType();
    if (parameterType.isAssignableFrom(Iterable.class)) {
      ParameterizedType listParamType = (ParameterizedType) parameter.getParameterizedType();
      Class<?> listParamActualTypeClass = getActualTypeClass(listParamType.getActualTypeArguments()[0]);

      List<Object> copiedList = new ArrayList<>();
      for (Object element : (Iterable<?>) value) {
        copiedList.add(adaptToProtoFieldCompatibleValueImpl(element, fieldInfo, listParamActualTypeClass));
      }
      return copiedList;
    } else if (parameterType.isAssignableFrom(Map.class)) {
      ParameterizedType mapParamType = (ParameterizedType) parameter.getParameterizedType();
      Class<?> keyActualType = getActualTypeClass(mapParamType.getActualTypeArguments()[0]);
      Class<?> valueActualType = getActualTypeClass(mapParamType.getActualTypeArguments()[1]);

      Map<Object, Object> copiedMap = new HashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        Object adaptedKey = adaptToProtoFieldCompatibleValueImpl(entry.getKey(), fieldInfo, keyActualType);
        Object adaptedValue = adaptToProtoFieldCompatibleValueImpl(entry.getValue(), fieldInfo, valueActualType);
        copiedMap.put(adaptedKey, adaptedValue);
      }
      return copiedMap;
    }

    return adaptToProtoFieldCompatibleValueImpl(value, fieldInfo, parameter.getType());
  }

  private static Object adaptToProtoFieldCompatibleValueImpl(Object value, FieldInfo fieldInfo, Class<?> parameterType) {
    if (parameterType.equals(int.class) || parameterType.equals(Integer.class)) {
      return intCheckedCast((long) value);
    } else if (Internal.EnumLite.class.isAssignableFrom(parameterType)) {
        // CEL coerces enums into int. We need to adapt it back into an actual proto enum.
        Method method;
        try {
          method = parameterType.getMethod("forNumber", int.class);
        } catch (NoSuchMethodException e) {
          throw new LinkageError(
              String.format("forNumber method does not exist on the enum: %s.", parameterType),
              e);
        }
        try {
          return method.invoke(null, intCheckedCast((long) value));
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
          throw new LinkageError(
              String.format("forNumber invocation failed on the enum: %s.", parameterType),
              e);
        }
    } else if (parameterType.equals(Int32Value.class)) {
      return Int32Value.of(intCheckedCast((long) value));
    } else if (parameterType.equals(Any.class)) {
      // TODO: Refactor ProtoAdapter and use that instead here
      return adaptValueToAny(value, fieldInfo);
    } else if (parameterType.equals(Value.class)) {
      return CelProtoJsonAdapter.adaptValueToJsonValue(value);
    } else if (parameterType.equals(Struct.class)) {
      return CelProtoJsonAdapter.adaptToJsonStructValue((Map) value);
    }

    return value;
  }

  private static Any adaptValueToAny(Object value, FieldInfo fieldInfo) {
    // TODO: Look into refactoring ProtoAdapter and use that instead here
    ByteString anyBytes = null;
    String typeUrl = "";
    if (value instanceof MessageLite) {
      anyBytes = ((MessageLite) value).toByteString();
      if (value instanceof Duration) {
        typeUrl = CelTypes.DURATION_MESSAGE;
      } else if (value instanceof Timestamp) {
        typeUrl = CelTypes.TIMESTAMP_MESSAGE;
      } else {
        typeUrl = fieldInfo.getFullyQualifiedProtoName();
      }
    } else if (value instanceof ByteString) {
      anyBytes = BytesValue.of((ByteString) value).toByteString();
      typeUrl = CelTypes.BYTES_WRAPPER_MESSAGE;
    } else if (value instanceof Boolean) {
      anyBytes = BoolValue.of((boolean) value).toByteString();
      typeUrl = CelTypes.BOOL_WRAPPER_MESSAGE;
    } else if (value instanceof String) {
      anyBytes = StringValue.of((String) value).toByteString();
      typeUrl = CelTypes.STRING_WRAPPER_MESSAGE;
    } else if (value instanceof Double) {
      anyBytes = DoubleValue.of((double) value).toByteString();
      typeUrl = CelTypes.DOUBLE_WRAPPER_MESSAGE;
    } else if (value instanceof Long) {
      anyBytes = Int64Value.of((long) value).toByteString();
      typeUrl = CelTypes.INT64_WRAPPER_MESSAGE;
    } else if (value instanceof UnsignedLong) {
      anyBytes = UInt64Value.of(((UnsignedLong) value).longValue()).toByteString();
      typeUrl = CelTypes.UINT64_WRAPPER_MESSAGE;
    }
    return Any.newBuilder()
        .setValue(anyBytes)
        .setTypeUrl("type.googleapis.com/" + typeUrl).build();
  }

  private static int intCheckedCast(long value) {
    try {
      return Ints.checkedCast(value);
    } catch (IllegalArgumentException e) {
      throw new CelRuntimeException(e, CelErrorCode.NUMERIC_OVERFLOW);
    }
  }

  private static Class<?> getActualTypeClass(Type paramType) {
    if (paramType instanceof WildcardType) {
      return (Class<?>) ((WildcardType) paramType).getUpperBounds()[0];
    }

    return (Class<?>) paramType;
  }

  public static ProtoMessageLiteValueProvider newInstance(CelLiteDescriptorPool celLiteDescriptorPool) {
    return new ProtoMessageLiteValueProvider(celLiteDescriptorPool);
  }

  private ProtoMessageLiteValueProvider(CelLiteDescriptorPool celLiteDescriptorPool) {
    this.descriptorPool = celLiteDescriptorPool;
  }
}
