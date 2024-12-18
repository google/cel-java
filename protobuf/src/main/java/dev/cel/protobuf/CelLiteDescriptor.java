package dev.cel.protobuf;

import dev.cel.common.annotations.Internal;

@Internal
public abstract class CelLiteDescriptor {
  private final String fullyQualifiedProtoName;
  private final String fullyQualifiedProtoJavaClassName;

  protected class FieldNameToGetter {
    private final Class<?> javaType;
    private final String getterName;

    protected FieldNameToGetter(Class<?> javaType, String getterName) {
      this.javaType = javaType;
      this.getterName = getterName;
    }
  }

  public CelLiteDescriptor(
      String fullyQualifiedProtoName,
      String fullyQualifiedProtoJavaClassName) {
    this.fullyQualifiedProtoName = fullyQualifiedProtoName;
    this.fullyQualifiedProtoJavaClassName = fullyQualifiedProtoJavaClassName;
  }
}
