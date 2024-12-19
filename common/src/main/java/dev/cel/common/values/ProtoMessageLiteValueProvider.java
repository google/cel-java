package dev.cel.common.values;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    // for (Method m : msgBuilder.getClass().getMethods()) {
    //   if (m.getName().equals("setSingleInt32")) {
    //     System.out.println();
    //   }
    // }
    MessageLite.Builder msgBuilder = msg.toBuilder();
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      FieldInfo fieldInfo = messageInfo.getFieldInfoMap().get(entry.getKey());
      Method setterMethod;
      try {
        setterMethod = msgBuilder.getClass().getMethod(fieldInfo.getSetterName(), fieldInfo.getJavaType());
      } catch (NoSuchMethodException e) {
        throw new LinkageError(
            String.format("setter method %s does not exist in class: %s.", fieldInfo.getSetterName(), messageInfo.getFullyQualifiedProtoName()),
            e);
      }

      try {
        Object newFieldValue = adaptToProtoFieldCompatibleValue(entry.getValue(), fieldInfo.getJavaType());
        setterMethod.invoke(msgBuilder, newFieldValue);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new LinkageError(
            String.format("setter method %s invocation failed for class: %s.", fieldInfo.getSetterName(), messageInfo.getFullyQualifiedProtoName()),
            e);
      }
    }

    return Optional.of(ProtoMessageLiteValue.create(msgBuilder.build(), messageInfo.getFullyQualifiedProtoName()));
  }

  private static Object adaptToProtoFieldCompatibleValue(Object value, Class<?> javaType) {
    if (javaType.equals(int.class)) {
      return Ints.checkedCast((Long) value);
    }

    return value;
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
