package dev.cel.protobuf;

import dev.cel.common.annotations.Internal;

@Internal
public abstract class CelLiteDescriptor {
  private final String fullyQualifiedProtoMessageName;
  private final String fullyQualifiedJavaClassName;

  protected class FieldNameToGetter {
    private final Class<?> javaType;
    private final String getterName;

    protected FieldNameToGetter(Class<?> javaType, String getterName) {
      this.javaType = javaType;
      this.getterName = getterName;
    }
  }

  public CelLiteDescriptor(String fullyQualifiedProtoMessageName,
      String fullyQualifiedJavaClassName) {
    this.fullyQualifiedProtoMessageName = fullyQualifiedProtoMessageName;
    this.fullyQualifiedJavaClassName = fullyQualifiedJavaClassName;
  }
}
