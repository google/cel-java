// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.protobuf;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.ceil;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import dev.cel.common.annotations.Internal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for code generated CEL lite descriptors to extend from.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
@Immutable
public abstract class CelLiteDescriptor {
  @SuppressWarnings("Immutable") // Copied to unmodifiable map
  private final Map<String, MessageDescriptor> protoFqnToDescriptors;

  @SuppressWarnings("Immutable") // Copied to unmodifiable map
  private final Map<String, MessageDescriptor> protoJavaClassNameToDescriptors;

  public Map<String, MessageDescriptor> getProtoTypeNamesToDescriptors() {
    return protoFqnToDescriptors;
  }

  public Map<String, MessageDescriptor> getProtoJavaClassNameToDescriptors() {
    return protoJavaClassNameToDescriptors;
  }

  /**
   * Contains a collection of classes which describe protobuf messagelite types.
   *
   * <p>CEL Library Internals. Do Not Use.
   */
  @Internal
  @Immutable
  public static final class MessageDescriptor {
    private final String fullyQualifiedProtoName;
    private final String fullyQualifiedProtoJavaClassName;

    @SuppressWarnings("Immutable") // Copied to unmodifiable map
    private final Map<String, FieldDescriptor> fieldInfoMap;

    public String getFullyQualifiedProtoName() {
      return fullyQualifiedProtoName;
    }

    public String getFullyQualifiedProtoJavaClassName() {
      return fullyQualifiedProtoJavaClassName;
    }

    public Map<String, FieldDescriptor> getFieldInfoMap() {
      return fieldInfoMap;
    }

    public MessageDescriptor(
        String fullyQualifiedProtoName,
        String fullyQualifiedProtoJavaClassName,
        Map<String, FieldDescriptor> fieldInfoMap) {
      this.fullyQualifiedProtoName = checkNotNull(fullyQualifiedProtoName);
      this.fullyQualifiedProtoJavaClassName = checkNotNull(fullyQualifiedProtoJavaClassName);
      // This is a cheap operation. View over the existing map with mutators disabled.
      this.fieldInfoMap = checkNotNull(Collections.unmodifiableMap(fieldInfoMap));
    }
  }

  /**
   * Describes a field of a protobuf messagelite type.
   *
   * <p>CEL Library Internals. Do Not Use.
   */
  @Internal
  @Immutable
  public static final class FieldDescriptor {
    private final String fullyQualifiedProtoName;
    private final JavaType javaType;
    private final String methodSuffixName;
    private final String fieldJavaClassName;
    private final ValueType celFieldValueType;
    private final Type protoFieldType;
    private final boolean hasHasser;
    private final String fieldProtoTypeName;

    /** Enumeration of the value type. */
    public enum ValueType {
      SCALAR,
      LIST,
      MAP
    }

    /**
     * Enumeration of the java type.
     *
     * <p>This is exactly the same as com.google.protobuf.Descriptors#JavaType
     */
    public enum JavaType {
      INT,
      LONG,
      FLOAT,
      DOUBLE,
      BOOLEAN,
      STRING,
      BYTE_STRING,
      ENUM,
      MESSAGE
    }

    /**
     * Enumeration of the protobuf type.
     *
     * <p>This is exactly the same as com.google.protobuf.Descriptors#Type
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
      SINT64
    }

    // Lazily-loaded field
    @SuppressWarnings("Immutable")
    private volatile Class<?> fieldJavaType;

    public Class<?> getFieldJavaClass() {
      if (fieldJavaType == null) {
        synchronized (this) {
          if (fieldJavaType == null) {
            fieldJavaType = loadFieldTypeClass();
          }
        }
      }
      return fieldJavaType;
    }

    public JavaType getJavaType() {
      return javaType;
    }

    public String getMethodSuffixName() {
      return methodSuffixName;
    }

    public String getSetterName() {
      String prefix = "";
      switch (celFieldValueType) {
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
      switch (celFieldValueType) {
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

    public ValueType getCelFieldValueType() {
      return celFieldValueType;
    }

    public Type getProtoFieldType() {
      return protoFieldType;
    }

    public boolean getHasHasser() {
      return hasHasser && celFieldValueType.equals(ValueType.SCALAR);
    }

    public String getFullyQualifiedProtoName() {
      return fullyQualifiedProtoName;
    }

    public String getFieldProtoTypeName() {
      return fieldProtoTypeName;
    }

    /**
     * Must be public, used for codegen only. Do not use.
     *
     * @param fullyQualifiedProtoName Fully qualified protobuf type name including the namespace
     *     (ex: cel.expr.conformance.proto3.TestAllTypes)
     * @param javaTypeName Canonical Java type name (ex: Long, Double, Float, Message... see
     *     Descriptors#JavaType)
     * @param methodSuffixName Suffix used to decorate the getters/setters (eg: "foo" in "setFoo"
     *     and "getFoo")
     * @param celFieldValueType Describes whether the field is a scalar, list or a map with respect
     *     to CEL.
     * @param protoFieldType Protobuf Field Type (ex: INT32, SINT32, GROUP, MESSAGE... see
     *     Descriptors#Type)
     * @param hasHasser True if the message has a presence test method (ex: wrappers).
     * @param fieldJavaClassName Fully qualified Java class name for the field, including its
     *     package name. Empty if the field is a primitive.
     * @param fieldProtoTypeName Fully qualified protobuf type name for the field. Empty if the
     *     field is a primitive.
     */
    @Internal
    public FieldDescriptor(
        String fullyQualifiedProtoName,
        String javaTypeName,
        String methodSuffixName,
        String celFieldValueType, // LIST, MAP, SCALAR
        String protoFieldType, // INT32, SINT32, GROUP, MESSAGE... (See Descriptors#Type)
        String hasHasser, //
        String fieldJavaClassName,
        String fieldProtoTypeName) {
      this.fullyQualifiedProtoName = checkNotNull(fullyQualifiedProtoName);
      this.javaType = JavaType.valueOf(javaTypeName);
      this.methodSuffixName = checkNotNull(methodSuffixName);
      this.fieldJavaClassName = checkNotNull(fieldJavaClassName);
      this.celFieldValueType = ValueType.valueOf(checkNotNull(celFieldValueType));
      this.protoFieldType = Type.valueOf(protoFieldType);
      this.hasHasser = Boolean.parseBoolean(hasHasser);
      this.fieldProtoTypeName = checkNotNull(fieldProtoTypeName);
      this.fieldJavaType = getPrimitiveFieldTypeClass();
    }

    @SuppressWarnings("ReturnMissingNullable") // Avoid taking a dependency on jspecify.nullable.
    private Class<?> getPrimitiveFieldTypeClass() {
      if (celFieldValueType.equals(ValueType.LIST)) {
        return Iterable.class;
      } else if (celFieldValueType.equals(ValueType.MAP)) {
        return Map.class;
      }

      switch (javaType) {
        case INT:
          return int.class;
        case LONG:
          return long.class;
        case FLOAT:
          return float.class;
        case DOUBLE:
          return double.class;
        case BOOLEAN:
          return boolean.class;
        case STRING:
          return String.class;
        case BYTE_STRING:
          return ByteString.class;
        default:
          // Non-primitives must be lazily loaded during instantiation of the runtime environment,
          // where the generated messages are linked into the binary via java_lite_proto_library.
          return null;
      }
    }

    private Class<?> loadFieldTypeClass() {
      if (!javaType.equals(JavaType.ENUM) && !javaType.equals(JavaType.MESSAGE)) {
        throw new IllegalArgumentException("Unexpected java type name for " + javaType);
      }

      try {
        return Class.forName(fieldJavaClassName);
      } catch (ClassNotFoundException e) {
        throw new LinkageError(String.format("Could not find class %s", fieldJavaClassName), e);
      }
    }
  }

  protected CelLiteDescriptor(List<MessageDescriptor> messageInfoList) {
    Map<String, MessageDescriptor> protoFqnMap =
        new HashMap<>(getMapInitialCapacity(messageInfoList.size()));
    Map<String, MessageDescriptor> protoJavaClassNameMap =
        new HashMap<>(getMapInitialCapacity(messageInfoList.size()));
    for (MessageDescriptor msgInfo : messageInfoList) {
      protoFqnMap.put(msgInfo.getFullyQualifiedProtoName(), msgInfo);
      protoJavaClassNameMap.put(msgInfo.getFullyQualifiedProtoJavaClassName(), msgInfo);
    }

    this.protoFqnToDescriptors = Collections.unmodifiableMap(protoFqnMap);
    this.protoJavaClassNameToDescriptors = Collections.unmodifiableMap(protoJavaClassNameMap);
  }

  /**
   * Returns a capacity that is sufficient to keep the map from being resized as long as it grows no
   * larger than expectedSize and the load factor is ≥ its default (0.75).
   */
  private static int getMapInitialCapacity(int expectedSize) {
    if (expectedSize < 3) {
      return expectedSize + 1;
    }

    // See https://github.com/openjdk/jdk/commit/3e393047e12147a81e2899784b943923fc34da8e. 0.75 is
    // used as a load factor.
    return (int) ceil(expectedSize / 0.75);
  }
}
