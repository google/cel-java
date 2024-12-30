package dev.cel.common.values;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.ReflectionUtils;
import dev.cel.common.types.StructTypeReference;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/** ProtoMessageLiteValue is a struct value with protobuf support. */
@AutoValue
@Immutable
public abstract class ProtoMessageLiteValue extends StructValue<StringValue> {

  @Override
  public abstract MessageLite value();

  @Override
  public abstract StructTypeReference celType();

  abstract CelLiteDescriptorPool descriptorPool();

  abstract ProtoLiteCelValueConverter protoLiteCelValueConverter();

  @Override
  public boolean isZeroValue() {
    return value().getDefaultInstanceForType().equals(value());
  }

  @Override
  public CelValue select(StringValue field) {
    MessageInfo messageInfo = descriptorPool().findMessageInfoByTypeName(celType().name()).get();
    FieldInfo fieldInfo = messageInfo.getFieldInfoMap().get(field.value());
    Method getterMethod = ReflectionUtils.getMethod(value().getClass(), fieldInfo.getGetterName());
    Object selectedValue = ReflectionUtils.invoke(getterMethod, value());

    if (fieldInfo.getProtoFieldType().equals(FieldInfo.Type.UINT32)) {
      selectedValue = UnsignedLong.valueOf((int) selectedValue);
    } else if (fieldInfo.getProtoFieldType().equals(FieldInfo.Type.UINT64)) {
      selectedValue = UnsignedLong.valueOf((long) selectedValue);
    }

    return protoLiteCelValueConverter().fromJavaObjectToCelValue(selectedValue);
  }

  @Override
  public Optional<CelValue> find(StringValue field) {
        MessageInfo messageInfo = descriptorPool().findMessageInfoByTypeName(celType().name()).get();
    FieldInfo fieldInfo = messageInfo.getFieldInfoMap().get(field.value());
    Method hasserMethod;
    try {
      hasserMethod = value().getClass().getMethod(fieldInfo.getHasserName());
    } catch (NoSuchMethodException e) {
      throw new LinkageError(
          String.format("getter method %s does not exist in class: %s.", fieldInfo.getHasserName(), messageInfo.getFullyQualifiedProtoName()),
          e);
    }
    try {
      boolean presenceTestResult = (boolean) hasserMethod.invoke(null);
      if (!presenceTestResult) {
        return Optional.empty();
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new LinkageError(
          String.format("getter method %s invocation failed for class: %s.", fieldInfo.getSetterName(), messageInfo.getFullyQualifiedProtoName()),
          e);
    }

    return Optional.of(select(field));
  }

  public static ProtoMessageLiteValue create(
      MessageLite value,
      String protoFqn,
      CelLiteDescriptorPool descriptorPool,
      ProtoLiteCelValueConverter protoLiteCelValueConverter
      ) {
    Preconditions.checkNotNull(value);
    return new AutoValue_ProtoMessageLiteValue(
        value,
        StructTypeReference.create(protoFqn),
        descriptorPool,
        protoLiteCelValueConverter
        );
  }

  private FieldInfo findField(MessageInfo messageInfo, String fieldName) {
    return messageInfo.getFieldInfoMap().get(fieldName);
  }
}