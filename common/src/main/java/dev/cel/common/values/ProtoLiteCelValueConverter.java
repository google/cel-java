package dev.cel.common.values;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.ProtoLiteAdapter;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.Optional;

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
    String className = msg.getClass().getName();
    MessageInfo messageInfo = descriptorPool.findMessageInfoByClassName(className)
        .orElseThrow(() -> new NoSuchElementException("Could not find message info for class: " + className));
    WellKnownProto wellKnownProto = WellKnownProto.getByTypeName(messageInfo.getFullyQualifiedProtoName());

    if (wellKnownProto == null) {
      return ProtoMessageLiteValue.create(msg, messageInfo.getFullyQualifiedProtoName(), descriptorPool, this);
    }

    switch (wellKnownProto) {
      case ANY_VALUE:
        return unpackAnyMessage((Any) msg);
      default:
        return super.fromWellKnownProtoToCelValue(msg, wellKnownProto);
    }
  }

  private CelValue unpackAnyMessage(Any anyMsg) {
    String typeUrl = getTypeNameFromTypeUrl(anyMsg.getTypeUrl())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("malformed type URL: %s", anyMsg.getTypeUrl())));
    MessageInfo messageInfo = descriptorPool.findMessageInfoByTypeName(typeUrl)
        .orElseThrow(() -> new NoSuchElementException("Could not find message info for any packed message's type name: " + anyMsg));

    Method method;
    try {
      method = Class.forName(messageInfo.getFullyQualifiedProtoJavaClassName()).getMethod("parseFrom", ByteString.class);
    } catch (ClassNotFoundException e) {
      throw new LinkageError(String.format("Could not find class %s", messageInfo.getFullyQualifiedProtoJavaClassName()), e);
    } catch (NoSuchMethodException e) {
      throw new LinkageError(
          String.format("parseFrom method does not exist on the message: %s.", messageInfo.getFullyQualifiedProtoJavaClassName()),
          e);
    }

    ByteString packedBytes = anyMsg.getValue();
    try {
      MessageLite unpackedMsg = (MessageLite) method.invoke(null, packedBytes);
      return fromJavaObjectToCelValue(unpackedMsg);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new LinkageError(
          String.format("parseFrom invocation failed on class: %s", messageInfo.getFullyQualifiedProtoJavaClassName()),
          e);
    }
  }

  private static Optional<String> getTypeNameFromTypeUrl(String typeUrl) {
    int pos = typeUrl.lastIndexOf('/');
    if (pos != -1) {
      return Optional.of(typeUrl.substring(pos + 1));
    }
    return Optional.empty();
  }

  private ProtoLiteCelValueConverter(CelOptions celOptions, CelLiteDescriptorPool celLiteDescriptorPool) {
    super(celOptions);
    this.descriptorPool = celLiteDescriptorPool;
  }
}
