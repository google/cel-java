package dev.cel.common.values;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.ReflectionUtils;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
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

  /** Adapts the protobuf message field into {@link CelValue}. */
  public CelValue fromProtoMessageFieldToCelValue(MessageLite msg, FieldInfo fieldInfo) {
    checkNotNull(msg);
    checkNotNull(fieldInfo);

    Method getterMethod = ReflectionUtils.getMethod(msg.getClass(), fieldInfo.getGetterName());
    Object fieldValue = ReflectionUtils.invoke(getterMethod, msg);

    switch (fieldInfo.getProtoFieldType()) {
      case UINT32:
        fieldValue = UnsignedLong.valueOf((int) fieldValue);
        break;
      case UINT64:
        fieldValue = UnsignedLong.valueOf((long) fieldValue);
        break;
      default:
        break;
    }

    return fromJavaObjectToCelValue(fieldValue);
  }

  @Override
  public CelValue fromJavaObjectToCelValue(Object value) {
    checkNotNull(value);

    if (value instanceof MessageLite) {
      return fromProtoMessageToCelValue((MessageLite) value);
    } else if (value instanceof MessageLite.Builder) {
      return fromProtoMessageToCelValue(((MessageLite.Builder) value).build());
    } else if (value instanceof com.google.protobuf.Internal.EnumLite) {
      // Coerce proto enum values back into int
      Method method = ReflectionUtils.getMethod(value.getClass(), "getNumber");
      value = ReflectionUtils.invoke(method, value);
    }

    return super.fromJavaObjectToCelValue(value);
  }

  public CelValue fromProtoMessageToCelValue(MessageLite msg) {
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

    Method method = ReflectionUtils.getMethod(messageInfo.getFullyQualifiedProtoJavaClassName(), "parseFrom", ByteString.class);
    ByteString packedBytes = anyMsg.getValue();
    MessageLite unpackedMsg = (MessageLite) ReflectionUtils.invoke(method, null, packedBytes);

    return fromJavaObjectToCelValue(unpackedMsg);
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
