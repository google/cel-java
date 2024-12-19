package dev.cel.protobuf;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import java.util.Optional;

@Internal
@Immutable
public abstract class CelLiteDescriptor {
  private final ImmutableMap<String, MessageInfo> protoNameToMessageInfoMap;

  public Optional<MessageInfo> findMessageInfo(String protoFqn) {
    return Optional.ofNullable(protoNameToMessageInfoMap.get(protoFqn));
  }


  @Immutable
  @Internal
  public static final class MessageInfo {
    private final String fullyQualifiedProtoName;
    private final String fullyQualifiedProtoJavaClassName;
    private final ImmutableMap<String, FieldInfo> fieldInfoMap;

    public String getFullyQualifiedProtoName() {
      return fullyQualifiedProtoName;
    }

    public String getFullyQualifiedProtoJavaClassName() {
      return fullyQualifiedProtoJavaClassName;
    }

    public String getFullyQualifiedProtoJavaBuilderClassName() {
      return getFullyQualifiedProtoJavaClassName() + "$Builder";
    }

    public ImmutableMap<String, FieldInfo> getFieldInfoMap() {
      return fieldInfoMap;
    }

    public MessageInfo(
        String fullyQualifiedProtoName,
        String fullyQualifiedProtoJavaClassName,
        ImmutableMap<String, FieldInfo> fieldInfoMap
    ) {
      this.fullyQualifiedProtoName = fullyQualifiedProtoName;
      this.fullyQualifiedProtoJavaClassName = fullyQualifiedProtoJavaClassName;
      this.fieldInfoMap = fieldInfoMap;
    }
  }

  @Internal
  @Immutable
  public static final class FieldInfo {
    private final Class<?> javaType;
    private final String javaTypeName;
    private final String methodSuffixName;

    public Class<?> getJavaType() {
      return javaType;
    }

    public String getJavaTypeName() {
      return javaTypeName;
    }

    public String getMethodSuffixName() {
      return methodSuffixName;
    }

    public String getSetterName() {
      return "set" + getMethodSuffixName();
    }

    public String getGetterName() {
      return "get" + getMethodSuffixName();
    }

    public FieldInfo(String javaTypeName, String methodSuffixName) {
      this.javaTypeName = javaTypeName;
      this.javaType = getClass(javaTypeName);
      this.methodSuffixName = methodSuffixName;
    }

    private static Class<?> getClass(String javaTypeName) {

     switch (javaTypeName) {
        case "INT":
          return int.class;
        case "LONG":
          return long.class;
        case "FLOAT":
          return float.class;
        case "DOUBLE":
          return double.class;
        case "BOOLEAN":
          return boolean.class;
        case "STRING":
          return String.class;
        case "BYTE_STRING":
          return Void.class;
        case "ENUM":
          return Void.class;
        case "MESSAGE":
          return Void.class;
      }

      throw new IllegalArgumentException("Unexpected java type name for " + javaTypeName);
    }
  }

  protected CelLiteDescriptor(ImmutableMap<String, MessageInfo> protoNameToMessageInfoMap) {
    this.protoNameToMessageInfoMap = protoNameToMessageInfoMap;
  }
}
