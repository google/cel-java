package dev.cel.common.values;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Internal;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.BidiConverter;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.protobuf.CelLiteDescriptor;
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
  private final ImmutableSet<CelLiteDescriptor> descriptors;

  @Override
  public Optional<CelValue> newValue(String structType, Map<String, Object> fields) {
    // TODO: Move this logic into a pool
    MessageInfo messageInfo = descriptors.stream()
        .map(descriptor -> descriptor.findMessageInfo(structType))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findAny()
        .orElse(null);

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

      Parameter[] parameters = setterMethod.getParameters();
      System.out.println(parameters[0]);

      try {
        Object newFieldValue = adaptToProtoFieldCompatibleValue(entry.getValue(), parameters[0]);
        setterMethod.invoke(msgBuilder, newFieldValue);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new LinkageError(
            String.format("setter method %s invocation failed for class: %s.", fieldInfo.getSetterName(), messageInfo.getFullyQualifiedProtoName()),
            e);
      }
    }

    return Optional.of(ProtoMessageLiteValue.create(msgBuilder.build(), messageInfo.getFullyQualifiedProtoName()));
  }

  private static final BidiConverter<Long, Integer> INT_CONVERTER =
      BidiConverter.of(Ints::checkedCast, Integer::longValue);


  private static Object adaptToProtoFieldCompatibleValue(Object value, Parameter parameter) {
    Class<?> parameterType = parameter.getType();
    if (parameterType.isAssignableFrom(Iterable.class)) {
      ParameterizedType listParamType = (ParameterizedType) parameter.getParameterizedType();
      Class<?> listParamActualTypeClass = getActualTypeClass(listParamType.getActualTypeArguments()[0]);

      List<Object> copiedList = new ArrayList<>();
      for (Object element : (Iterable<?>) value) {
        copiedList.add(adaptToProtoFieldCompatibleValueImpl(element, listParamActualTypeClass));
      }
      return copiedList;
    } else if (parameterType.isAssignableFrom(Map.class)) {
      ParameterizedType mapParamType = (ParameterizedType) parameter.getParameterizedType();
      Class<?> keyActualType = getActualTypeClass(mapParamType.getActualTypeArguments()[0]);
      Class<?> valueActualType = getActualTypeClass(mapParamType.getActualTypeArguments()[1]);

      Map<Object, Object> copiedMap = new HashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        Object adaptedKey = adaptToProtoFieldCompatibleValueImpl(entry.getKey(), keyActualType);
        Object adaptedValue = adaptToProtoFieldCompatibleValueImpl(entry.getValue(), valueActualType);
        copiedMap.put(adaptedKey, adaptedValue);
      }
      return copiedMap;
    }

    return adaptToProtoFieldCompatibleValueImpl(value, parameter.getType());
  }

  private static Object adaptToProtoFieldCompatibleValueImpl(Object value, Class<?> parameterType) {
    if (parameterType.equals(int.class) || parameterType.equals(Integer.class)) {
      return Ints.checkedCast((Long) value);
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
          return method.invoke(null, Ints.checkedCast((Long) value));
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new LinkageError(
              String.format("forNumber invocation failed on the enum: %s.", parameterType),
              e);
        }
    }

    return value;
  }

  private static Class<?> getActualTypeClass(Type paramType) {
    if (paramType instanceof WildcardType) {
      return (Class<?>) ((WildcardType) paramType).getUpperBounds()[0];
    }

    return (Class<?>) paramType;
  }

  public static ProtoMessageLiteValueProvider newInstance(CelLiteDescriptor... descriptors) {
    return newInstance(ImmutableSet.copyOf(descriptors));
  }

  public static ProtoMessageLiteValueProvider newInstance(Iterable<CelLiteDescriptor> descriptors) {
    return new ProtoMessageLiteValueProvider(descriptors);
  }

  private ProtoMessageLiteValueProvider(Iterable<CelLiteDescriptor> descriptors) {
    this.descriptors = ImmutableSet.copyOf(descriptors);
  }
}
