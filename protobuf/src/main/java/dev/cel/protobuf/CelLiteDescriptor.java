package dev.cel.protobuf;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import dev.cel.common.annotations.Internal;
import java.util.Map;

/**
 * Base class for code generated CEL descriptors to extend from.
 */
@Internal
@Immutable
public abstract class CelLiteDescriptor {
  private final ImmutableMap<String, MessageInfo> protoFqnToMessageInfo;
  private final ImmutableMap<String, MessageInfo> protoJavaClassNameToMessageInfo;

  public ImmutableMap<String, MessageInfo> getProtoFqnToMessageInfo() {
    return protoFqnToMessageInfo;
  }
  public ImmutableMap<String, MessageInfo> getProtoJavaClassNameToMessageInfo() {
    return protoJavaClassNameToMessageInfo;
  }

  @Internal
  @Immutable
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

    public ImmutableMap<String, FieldInfo> getFieldInfoMap() {
      return fieldInfoMap;
    }

    public MessageInfo(
        String fullyQualifiedProtoName,
        String fullyQualifiedProtoJavaClassName,
        ImmutableMap<String, FieldInfo> fieldInfoMap
    ) {
      this.fullyQualifiedProtoName = checkNotNull(fullyQualifiedProtoName);
      this.fullyQualifiedProtoJavaClassName = checkNotNull(fullyQualifiedProtoJavaClassName);
      this.fieldInfoMap = checkNotNull(fieldInfoMap);
    }
  }

  @Internal
  @Immutable
  public static final class FieldInfo {
    private final String fullyQualifiedProtoName;
    private final String javaTypeName;
    private final String methodSuffixName;
    private final String fieldJavaClassName;
    private final ValueType fieldValueType;
    private final Type protoFieldType;
    private final boolean hasHasser;

    public enum ValueType {
      SCALAR,
      LIST,
      MAP
    }

    /**
     * Mirror of Descriptors#Type
     */
    public enum Type {
      DOUBLE,
      FLOAT,
      INT64,
      UINT64,
      INT32,
      FIXED64,
      FIXED32,
      BOOL,
      STRING,
      GROUP,
      MESSAGE,
      BYTES,
      UINT32,
      ENUM,
      SFIXED32,
      SFIXED64,
      SINT32,
      SINT64;
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
      switch (fieldValueType) {
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
      String suffix = "";
      switch (fieldValueType) {
        case SCALAR:
          break;
        case LIST:
          suffix = "List";
          break;
        case MAP:
          suffix = "Map";
          break;
      }
      return "get" + getMethodSuffixName() + suffix;
    }

    public String getHasserName() {
      return "has" + getMethodSuffixName();
    }

    public String getFieldJavaClassName() {
      return fieldJavaClassName;
    }

    public ValueType getFieldValueType() {
      return fieldValueType;
    }

    public Type getProtoFieldType() {
      return protoFieldType;
    }

    public boolean getHasHasser() {
      return hasHasser;
    }

    public String getFullyQualifiedProtoName() {
      return fullyQualifiedProtoName;
    }

    /**
     * Must be public, used for codegen only. Do not use.
     */
    @Internal
    public FieldInfo(
        String fullyQualifiedProtoName,
        String javaTypeName, // Long, Double, Float, Message... (See Descriptors#JavaType)
        String methodSuffixName,
        String fieldJavaClassName,
        String fieldValueType, // LIST, MAP, SCALAR
        String protoFieldType,
        String hasHasser
        ) {
      this.fullyQualifiedProtoName = checkNotNull(fullyQualifiedProtoName);
      this.javaTypeName = checkNotNull(javaTypeName);
      this.methodSuffixName = checkNotNull(methodSuffixName);
      this.fieldJavaClassName = checkNotNull(fieldJavaClassName);
      this.fieldValueType = ValueType.valueOf(checkNotNull(fieldValueType));
      this.protoFieldType = Type.valueOf(protoFieldType);
      this.hasHasser = Boolean.parseBoolean(hasHasser);
    }

    private Class<?> deriveFieldTypeClass() {
      if (fieldValueType.equals(ValueType.LIST)) {
        return Iterable.class;
      } else if (fieldValueType.equals(ValueType.MAP)) {
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
