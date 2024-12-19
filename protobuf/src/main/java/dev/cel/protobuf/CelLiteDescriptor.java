package dev.cel.protobuf;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;

@Internal
@Immutable
public abstract class CelLiteDescriptor {
  private final ImmutableMap<String, MessageInfo> protoNameToMessageInfoMap;

  @Immutable
  @Internal
  public static final class MessageInfo {
    private final String fullyQualifiedProtoName;
    private final String fullyQualifiedProtoJavaClassName;
    private final ImmutableMap<String, FieldNameToGetter> fieldNameToGetters;

    public String getFullyQualifiedProtoName() {
      return fullyQualifiedProtoName;
    }

    public String getFullyQualifiedProtoJavaClassName() {
      return fullyQualifiedProtoJavaClassName;
    }

    public ImmutableMap<String, FieldNameToGetter> getFieldNameToGetters() {
      return fieldNameToGetters;
    }

    public MessageInfo(
        String fullyQualifiedProtoName,
        String fullyQualifiedProtoJavaClassName,
        ImmutableMap<String, FieldNameToGetter> fieldNameToGetters
    ) {
      this.fullyQualifiedProtoName = fullyQualifiedProtoName;
      this.fullyQualifiedProtoJavaClassName = fullyQualifiedProtoJavaClassName;
      this.fieldNameToGetters = fieldNameToGetters;
    }
  }

  @Internal
  @Immutable
  public static final class FieldNameToGetter {
    private final Class<?> javaType;
    private final String getterName;

    public Class<?> getJavaType() {
      return javaType;
    }

    public String getGetterName() {
      return getterName;
    }

    public FieldNameToGetter(String getterName) {
      this.javaType = null; // TODO
      this.getterName = getterName;
    }

    public FieldNameToGetter(Class<?> javaType, String getterName) {
      this.javaType = javaType;
      this.getterName = getterName;
    }
  }

  protected CelLiteDescriptor(ImmutableMap<String, MessageInfo> protoNameToMessageInfoMap) {
    this.protoNameToMessageInfoMap = protoNameToMessageInfoMap;
  }
}
