package dev.cel.common.values;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.types.CelType;
import dev.cel.common.types.StructTypeReference;
import java.util.Optional;

/** ProtoMessageLiteValue is a struct value with protobuf support. */
@AutoValue
@Immutable
public abstract class ProtoMessageLiteValue extends StructValue<StringValue> {

  @Override
  public abstract MessageLite value();

  @Override
  public abstract CelType celType();

  @Override
  public boolean isZeroValue() {
    return value().getDefaultInstanceForType().equals(value());
  }

  @Override
  public CelValue select(StringValue field) {
    throw new IllegalArgumentException("Unimplemented");
  }

  @Override
  public Optional<CelValue> find(StringValue field) {
    throw new IllegalArgumentException("Unimplemented");
  }

  public static ProtoMessageLiteValue create(
      MessageLite value,
      String protoFqn) {
    Preconditions.checkNotNull(value);
    return new AutoValue_ProtoMessageLiteValue(
        value,
        StructTypeReference.create(protoFqn));
  }
}