package dev.cel.protobuf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.cel.common.annotations.Internal;

@Internal
public abstract class CelLiteDescriptor {
  private final ImmutableMap<String, MessageInfo> protoNameToMessageInfoMap;

  public static class MessageInfo {
    private final String fullyQualifiedProtoName;
    private final String fullyQualifiedProtoJavaClassName;

    public String getFullyQualifiedProtoName() {
      return fullyQualifiedProtoName;
    }

    public String getFullyQualifiedProtoJavaClassName() {
      return fullyQualifiedProtoJavaClassName;
    }

    public MessageInfo(String fullyQualifiedProtoName, String fullyQualifiedProtoJavaClassName) {
      this.fullyQualifiedProtoName = fullyQualifiedProtoName;
      this.fullyQualifiedProtoJavaClassName = fullyQualifiedProtoJavaClassName;
    }
  }

  protected class FieldNameToGetter {
    private final Class<?> javaType;
    private final String getterName;

    protected FieldNameToGetter(Class<?> javaType, String getterName) {
      this.javaType = javaType;
      this.getterName = getterName;
    }
  }

  protected CelLiteDescriptor(ImmutableMap<String, MessageInfo> messageInfoList) {
    ImmutableMap.Builder<String, MessageInfo> builder = ImmutableMap.builder();
    // for (MessageInfo messageInfo : messageInfoList) {
    //   builder.put(messageInfo.fullyQualifiedProtoName, messageInfo);
    // }
    this.protoNameToMessageInfoMap = builder.build();
  }
}
