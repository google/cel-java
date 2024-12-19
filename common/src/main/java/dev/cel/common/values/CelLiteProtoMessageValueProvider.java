package dev.cel.common.values;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.util.Map;
import java.util.Optional;

@Immutable
public class CelLiteProtoMessageValueProvider implements CelValueProvider {
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

    return Optional.of(ProtoMessageLiteValue.create(msg, messageInfo.getFullyQualifiedProtoName()));
  }

  public static CelLiteProtoMessageValueProvider newInstance(CelLiteDescriptor... descriptors) {
    return newInstance(ImmutableSet.copyOf(descriptors));
  }

  public static CelLiteProtoMessageValueProvider newInstance(Iterable<CelLiteDescriptor> descriptors) {
    return new CelLiteProtoMessageValueProvider(descriptors);
  }

  private CelLiteProtoMessageValueProvider(Iterable<CelLiteDescriptor> descriptors) {
    this.descriptors = ImmutableSet.copyOf(descriptors);
  }
}
