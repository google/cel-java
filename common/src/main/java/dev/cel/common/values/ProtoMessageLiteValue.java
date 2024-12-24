package dev.cel.common.values;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.types.CelType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.protobuf.CelLiteDescriptor;
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
    try {
      Method getterMethod = value().getClass().getMethod(fieldInfo.getGetterName());
      Object selectedValue = getterMethod.invoke(value());
      return protoLiteCelValueConverter().fromJavaObjectToCelValue(selectedValue);
    } catch (NoSuchMethodException e) {
      // TODO: Exceptions
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Optional<CelValue> find(StringValue field) {
    throw new IllegalArgumentException("Unimplemented");
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