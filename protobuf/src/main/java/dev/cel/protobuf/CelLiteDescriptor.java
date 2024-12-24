package dev.cel.protobuf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import dev.cel.common.annotations.Internal;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for code generated CEL descriptors to extend from.
 */
@Internal
@Immutable
public abstract class CelLiteDescriptor {
  private final ImmutableMap<String, MessageInfo> protoFqnToMessageInfo;
  private final ImmutableMap<String, MessageInfo> protoJavaClassNameToMessageInfo;

  public Optional<MessageInfo> findMessageInfo(String protoFqn) {
    return Optional.ofNullable(protoFqnToMessageInfo.get(protoFqn));
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
    private final String fullyQualifiedProtoName;
    private final String javaTypeName;
    private final String methodSuffixName;
    private final String fieldJavaClassName;
    private final Type fieldType;

    public enum Type {
      SCALAR,
      LIST,
      MAP
    }

    // Lazily-loaded field
    @SuppressWarnings("Immutable")
    private volatile Class<?> fieldJavaType;

    public Class<?> getFieldJavaClass() {
      if (fieldJavaType == null) {
        synchronized (this) {
          if (fieldJavaType == null) {
            fieldJavaType = deriveFieldTypeClass();
          }
        }
      }
      return fieldJavaType;
    }

    public String getJavaTypeName() {
      return javaTypeName;
    }

    public String getMethodSuffixName() {
      return methodSuffixName;
    }

    public String getSetterName() {
      String prefix = "";
      switch (fieldType) {
        case SCALAR:
          prefix = "set";
          break;
        case LIST:
          prefix = "addAll";
          break;
        case MAP:
          prefix = "putAll";
          break;
      }
      return prefix + getMethodSuffixName();
    }

    public String getGetterName() {
      return "get" + getMethodSuffixName();
    }

    public String getFieldJavaClassName() {
      return fieldJavaClassName;
    }

    public Type getFieldType() {
      return fieldType;
    }

    public String getFullyQualifiedProtoName() {
      return fullyQualifiedProtoName;
    }

    public FieldInfo(
        String fullyQualifiedProtoName,
        String javaTypeName,
        String methodSuffixName,
        String fieldJavaClassName,
        String fieldType
        ) {
      this.fullyQualifiedProtoName = fullyQualifiedProtoName;
      this.javaTypeName = javaTypeName;
      this.methodSuffixName = methodSuffixName;
      this.fieldJavaClassName = fieldJavaClassName;
      this.fieldType = Type.valueOf(fieldType);
    }

    private Class<?> deriveFieldTypeClass() {
      if (fieldType.equals(Type.LIST)) {
        return Iterable.class;
      } else if (fieldType.equals(Type.MAP)) {
        return Map.class;
      }

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
          return ByteString.class;
        case "ENUM":
        case "MESSAGE":
          // TODO: Handle WKTs separately
          try {
            return Class.forName(fieldJavaClassName);
          } catch (ClassNotFoundException e) {
            throw new LinkageError(String.format("Could not find class %s", fieldJavaClassName), e);
          }
      }

      throw new IllegalArgumentException("Unexpected java type name for " + javaTypeName);
    }
  }

  protected CelLiteDescriptor(ImmutableList<MessageInfo> messageInfoList) {
    ImmutableMap.Builder<String, MessageInfo> protoFqnMapBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, MessageInfo> protoJavaClassNameMapBuilder = ImmutableMap.builder();
    for (MessageInfo msgInfo : messageInfoList) {
      protoFqnMapBuilder.put(msgInfo.getFullyQualifiedProtoName(), msgInfo);
      protoJavaClassNameMapBuilder.put(msgInfo.getFullyQualifiedProtoJavaClassName(), msgInfo);
    }

    this.protoFqnToMessageInfo = protoFqnMapBuilder.buildOrThrow();
    this.protoJavaClassNameToMessageInfo = protoJavaClassNameMapBuilder.buildOrThrow();
  }
}
