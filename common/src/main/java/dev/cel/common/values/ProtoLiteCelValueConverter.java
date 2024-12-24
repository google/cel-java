package dev.cel.common.values;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;

@Immutable
@Internal
public final class ProtoLiteCelValueConverter extends BaseProtoCelValueConverter {
  private final CelLiteDescriptorPool descriptorPool;

  public static ProtoLiteCelValueConverter newInstance(CelOptions celOptions, CelLiteDescriptorPool celLiteDescriptorPool) {
    return new ProtoLiteCelValueConverter(celOptions, celLiteDescriptorPool);
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
    // TODO: WKT
    String className = msg.getClass().getName();
    MessageInfo messageInfo = descriptorPool.findMessageInfoByClassName(className).orElse(null);
    WellKnownProto wellKnownProto = WellKnownProto.getByTypeName(messageInfo.getFullyQualifiedProtoName());

    if (wellKnownProto == null) {
      return ProtoMessageLiteValue.create(msg, messageInfo.getFullyQualifiedProtoName(), descriptorPool, this);
    }
    return null;

    // String className = msg.getClass().getName();
    // MessageInfo messageInfo = findMessageInfoByClassName(className).orElse(null);
    // System.out.println();
    // return null;
  }


  private ProtoLiteCelValueConverter(CelOptions celOptions, CelLiteDescriptorPool celLiteDescriptorPool) {
    super(celOptions);
    this.descriptorPool = celLiteDescriptorPool;
  }
}
