package dev.cel.common.values;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.ReflectionUtils;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.common.types.StructTypeReference;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.lang.reflect.Method;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
    if (fieldInfo.getProtoFieldType().equals(FieldInfo.Type.MESSAGE) &&
        WellKnownProto.isWrapperType(fieldInfo.getFieldProtoTypeName())) {
      PresenceTestResult presenceTestResult = presenceTest(field);
      // Special semantics for wrapper types per CEL spec. NullValue is returned instead of the default value for unset fields.
      if (!presenceTestResult.hasPresence()) {
        return NullValue.NULL_VALUE;
      }

      return presenceTestResult.selectedValue().get();
    }

    return protoLiteCelValueConverter().fromProtoMessageFieldToCelValue(value(), fieldInfo);
  }

  @Override
  public Optional<CelValue> find(StringValue field) {
    PresenceTestResult presenceTestResult = presenceTest(field);

    return presenceTestResult.selectedValue();
  }

  private PresenceTestResult presenceTest(StringValue field) {
    MessageInfo messageInfo = descriptorPool().findMessageInfoByTypeName(celType().name()).get();
    FieldInfo fieldInfo = messageInfo.getFieldInfoMap().get(field.value());
    CelValue selectedValue = null;
    boolean presenceTestResult;
    if (fieldInfo.getHasHasser()) {
      Method hasserMethod = ReflectionUtils.getMethod(value().getClass(), fieldInfo.getHasserName());
      presenceTestResult = (boolean) ReflectionUtils.invoke(hasserMethod, value());
    } else {
      // Lists, Maps and Opaque Values
      selectedValue = protoLiteCelValueConverter().fromProtoMessageFieldToCelValue(value(), fieldInfo);
      presenceTestResult = !selectedValue.isZeroValue();
    }

    if (!presenceTestResult) {
      return PresenceTestResult.create(null);
    }

    if (selectedValue == null) {
      selectedValue = protoLiteCelValueConverter().fromProtoMessageFieldToCelValue(value(), fieldInfo);
    }

    return PresenceTestResult.create(selectedValue);
  }

  @AutoValue
  abstract static class PresenceTestResult {
    abstract boolean hasPresence();
    abstract Optional<CelValue> selectedValue();

    static PresenceTestResult create(@Nullable CelValue presentValue) {
      Optional<CelValue> maybePresentValue = Optional.ofNullable(presentValue);
      return new AutoValue_ProtoMessageLiteValue_PresenceTestResult(maybePresentValue.isPresent(), maybePresentValue);
    }
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
}