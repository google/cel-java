package dev.cel.common.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.util.Optional;

@Immutable
@Internal
public final class CelLiteDescriptorPool {
  private final ImmutableMap<String, MessageInfo> protoFqnToMessageInfo;
  private final ImmutableMap<String, MessageInfo> protoJavaClassNameToMessageInfo;

  public static CelLiteDescriptorPool newInstance(ImmutableSet<CelLiteDescriptor> descriptors) {
    return new CelLiteDescriptorPool(descriptors);
  }

  public Optional<MessageInfo> findMessageInfoByTypeName(String protoFqn) {
    return Optional.ofNullable(protoFqnToMessageInfo.get(protoFqn));
  }

  public Optional<MessageInfo> findMessageInfoByClassName(String javaClassName) {
    return Optional.ofNullable(protoJavaClassNameToMessageInfo.get(javaClassName));
  }

  private CelLiteDescriptorPool(ImmutableSet<CelLiteDescriptor> descriptors) {
    ImmutableMap.Builder<String, MessageInfo> protoFqnMapBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, MessageInfo> protoJavaClassNameMapBuilder = ImmutableMap.builder();
    for (CelLiteDescriptor descriptor : descriptors) {
      protoFqnMapBuilder.putAll(descriptor.getProtoFqnToMessageInfo());
      protoJavaClassNameMapBuilder.putAll(descriptor.getProtoJavaClassNameToMessageInfo());
    }

    this.protoFqnToMessageInfo = protoFqnMapBuilder.buildOrThrow();
    this.protoJavaClassNameToMessageInfo = protoJavaClassNameMapBuilder.buildOrThrow();
  }
}
