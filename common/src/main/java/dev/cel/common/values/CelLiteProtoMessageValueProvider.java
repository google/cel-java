package dev.cel.common.values;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.protobuf.CelLiteDescriptor;
import java.util.Map;
import java.util.Optional;

@Immutable
public class CelLiteProtoMessageValueProvider implements CelValueProvider {
  private final ImmutableList<CelLiteDescriptor> descriptors;

  @Override
  public Optional<CelValue> newValue(String structType, Map<String, Object> fields) {
    return Optional.empty();
  }

  public static CelLiteProtoMessageValueProvider newInstance(CelLiteDescriptor... descriptors) {
    return newInstance(ImmutableList.copyOf(descriptors));
  }

  public static CelLiteProtoMessageValueProvider newInstance(Iterable<CelLiteDescriptor> descriptors) {
    return new CelLiteProtoMessageValueProvider(descriptors);
  }

  private CelLiteProtoMessageValueProvider(Iterable<CelLiteDescriptor> descriptors) {
    this.descriptors = ImmutableList.copyOf(descriptors);
  }
}
