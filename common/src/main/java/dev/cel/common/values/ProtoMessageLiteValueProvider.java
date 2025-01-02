package dev.cel.common.values;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.Internal;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.common.internal.ProtoLiteAdapter;
import dev.cel.common.internal.ReflectionUtils;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Immutable
public class ProtoMessageLiteValueProvider implements CelValueProvider {
  private static final ImmutableMap<String, WellKnownProto> CLASS_NAME_TO_WELL_KNOWN_PROTO_MAP;
  private final ProtoLiteCelValueConverter protoLiteCelValueConverter;
  private final CelLiteDescriptorPool descriptorPool;
  private final ProtoLiteAdapter protoLiteAdapter;

  static {
    CLASS_NAME_TO_WELL_KNOWN_PROTO_MAP =
        stream(WellKnownProto.values())
            .collect(toImmutableMap(WellKnownProto::javaClassName, Function.identity()));
  }

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

      Method setterMethod = ReflectionUtils.getMethod(msgBuilder.getClass(), fieldInfo.getSetterName(), fieldInfo.getFieldJavaClass());
      Object newFieldValue = adaptToProtoFieldCompatibleValue(entry.getValue(), fieldInfo, setterMethod.getParameters()[0]);
      ReflectionUtils.invoke(setterMethod, msgBuilder, newFieldValue);
    }

    return Optional.of(protoLiteCelValueConverter.fromProtoMessageToCelValue(msgBuilder.build()));
  }

  private Object adaptToProtoFieldCompatibleValue(Object value, FieldInfo fieldInfo, Parameter parameter) {
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

      Map<Object, Object> copiedMap = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        Object adaptedKey = adaptToProtoFieldCompatibleValueImpl(entry.getKey(), fieldInfo, keyActualType);
        Object adaptedValue = adaptToProtoFieldCompatibleValueImpl(entry.getValue(), fieldInfo, valueActualType);
        copiedMap.put(adaptedKey, adaptedValue);
      }
      return copiedMap;
    }

    return adaptToProtoFieldCompatibleValueImpl(value, fieldInfo, parameter.getType());
  }

  private Object adaptToProtoFieldCompatibleValueImpl(Object value, FieldInfo fieldInfo, Class<?> parameterType) {
    WellKnownProto wellKnownProto = CLASS_NAME_TO_WELL_KNOWN_PROTO_MAP.get(parameterType.getName());
    if (wellKnownProto != null) {
      switch (wellKnownProto) {
        case ANY_VALUE:
          String typeUrl = fieldInfo.getFieldProtoTypeName();
          if (value instanceof MessageLite) {
            typeUrl = descriptorPool.findMessageInfoByClassName(value.getClass().getName()).get().getFullyQualifiedProtoName();
          }
          return protoLiteAdapter.adaptValueToAny(value, typeUrl);
        default:
          return protoLiteAdapter.adaptValueToWellKnownProto(value, wellKnownProto);
      }
    }

    if (value instanceof UnsignedLong) {
      value = ((UnsignedLong)value).longValue();
    }

    if (parameterType.equals(int.class) || parameterType.equals(Integer.class)) {
      return intCheckedCast((long) value);
    } else if (parameterType.equals(float.class) || parameterType.equals(Float.class)) {
      return ((Double) value).floatValue();
    } else if (Internal.EnumLite.class.isAssignableFrom(parameterType)) {
        // CEL coerces enums into int. We need to adapt it back into an actual proto enum.
        Method method = ReflectionUtils.getMethod(parameterType, "forNumber", int.class);
        return ReflectionUtils.invoke(method, null, intCheckedCast((long) value));
    } else if (parameterType.equals(Any.class)) {
      return protoLiteAdapter.adaptValueToAny(value, fieldInfo.getFullyQualifiedProtoName());
    }

    return value;
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

  public static ProtoMessageLiteValueProvider newInstance(ProtoLiteCelValueConverter protoLiteCelValueConverter, ProtoLiteAdapter protoLiteAdapter, CelLiteDescriptorPool celLiteDescriptorPool) {
    return new ProtoMessageLiteValueProvider(protoLiteCelValueConverter, protoLiteAdapter, celLiteDescriptorPool);
  }

  private ProtoMessageLiteValueProvider(ProtoLiteCelValueConverter protoLiteCelValueConverter, ProtoLiteAdapter protoLiteAdapter, CelLiteDescriptorPool celLiteDescriptorPool) {
    this.protoLiteCelValueConverter = protoLiteCelValueConverter;
    this.descriptorPool = celLiteDescriptorPool;
    this.protoLiteAdapter = protoLiteAdapter;
  }
}
