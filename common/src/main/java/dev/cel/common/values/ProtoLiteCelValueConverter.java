package dev.cel.common.values;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.util.Optional;

@Immutable
@Internal
public final class ProtoLiteCelValueConverter extends BaseProtoCelValueConverter {
  // TODO: Turn this into a pool
  private final ImmutableSet<CelLiteDescriptor> descriptors;

  public static ProtoLiteCelValueConverter newInstance(CelOptions celOptions, ImmutableSet<CelLiteDescriptor> descriptors) {
    return new ProtoLiteCelValueConverter(celOptions, descriptors);
  }

  @Override
  public CelValue fromJavaObjectToCelValue(Object value) {
    Preconditions.checkNotNull(value);

    if (value instanceof MessageLite) {
      return fromProtoMessageToCelValue((MessageLite) value);
    } else if (value instanceof MessageLite.Builder) {
      return fromProtoMessageToCelValue(((MessageLite.Builder) value).build());
    }
    return super.fromJavaObjectToCelValue(value);
  }

  private CelValue fromProtoMessageToCelValue(MessageLite msg) {
    String className = msg.getClass().getName();
    System.out.println();
    return null;
  }

  private Optional<MessageInfo> findMessageInfoByName(String protoFqn) {
    // TODO: Move logic into pool
    return descriptors.stream()
        .map(descriptor -> descriptor.findMessageInfo(protoFqn))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findAny();
  }

  private ProtoLiteCelValueConverter(CelOptions celOptions, ImmutableSet<CelLiteDescriptor> descriptors) {
    super(celOptions);
    this.descriptors = descriptors;
  }
}
