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

import static java.lang.Math.ceil;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
@SuppressWarnings("ReturnMissingNullable") // Avoid taking a dependency on jspecify.nullable.
public abstract class CelLiteDescriptor {
  @SuppressWarnings("Immutable") // Copied to unmodifiable map
  private final Map<String, MessageLiteDescriptor> protoFqnToDescriptors;

  @SuppressWarnings("Immutable") // Copied to unmodifiable map
  private final Map<Class<?>, MessageLiteDescriptor> protoJavaClassNameToDescriptors;

  public Map<String, MessageLiteDescriptor> getProtoTypeNamesToDescriptors() {
    return protoFqnToDescriptors;
  }

  public Map<Class<?>, MessageLiteDescriptor> getProtoJavaClassNameToDescriptors() {
    return protoJavaClassNameToDescriptors;
  }

  /**
   * Contains a collection of classes which describe protobuf messagelite types.
   *
   * <p>CEL Library Internals. Do Not Use.
   */
  @Internal
  @Immutable
  public static final class MessageLiteDescriptor {
    private final String fullyQualifiedProtoTypeName;
    private final Class<?> clazz;

    @SuppressWarnings("Immutable") // Copied to unmodifiable map
    private final Map<String, FieldDescriptor> fieldInfoMap;

    public String getProtoTypeName() {
      return fullyQualifiedProtoTypeName;
    }

    public Class<?> getMessageClass() {
      return clazz;
    }
    public Map<String, FieldDescriptor> getFieldInfoMap() {
      return fieldInfoMap;
    }

    public MessageLiteDescriptor(
        String fullyQualifiedProtoTypeName,
        Class<?> clazz,
        Map<String, FieldDescriptor> fieldInfoMap) {
      this.fullyQualifiedProtoTypeName = checkNotNull(fullyQualifiedProtoTypeName);
      // this.clazz = clazz;
      this.clazz = clazz;
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
    private final JavaType javaType;
    private final String fieldJavaClassName;
    private final String fieldProtoTypeName;
    private final String fullyQualifiedProtoFieldName;
    private final String methodSuffixName;
    private final Type protoFieldType;
    private final CelFieldValueType celFieldValueType;
    private final boolean hasHasser;

    /**
     * Enumeration of the CEL field value type. This is analogous to the following from field
     * descriptors:
     *
     * <ul>
     *   <li>LIST: Repeated Field
     *   <li>MAP: Map Field
     *   <li>SCALAR: Neither of above (scalars, messages)
     * </ul>
     */
    public enum CelFieldValueType {
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
    private volatile Class<?> fieldJavaClass;

    /**
     * Returns the {@link Class} object for this field. In case of protobuf messages, the class
     * object is lazily loaded then memoized.
     */
    public Class<?> getFieldJavaClass() {
      if (fieldJavaClass == null) {
        synchronized (this) {
          if (fieldJavaClass == null) {
            fieldJavaClass = loadNonPrimitiveFieldTypeClass();
          }
        }
      }
      return fieldJavaClass;
    }

    /**
     * Gets the field's java type.
     *
     * <p>This is exactly the same as com.google.protobuf.Descriptors#JavaType
     */
    public JavaType getJavaType() {
      return javaType;
    }

    /**
     * Returns the method suffix name as part of getters or setters of the field in the protobuf
     * message's builder. (Ex: for a field named single_string, "SingleString" is returned).
     */
    public String getMethodSuffixName() {
      return methodSuffixName;
    }

    /**
     * Returns the setter name for the field used in protobuf message's builder (Ex:
     * setSingleString).
     */
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

    /**
     * Returns the getter name for the field used in protobuf message's builder (Ex:
     * getSingleString).
     */
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

    /**
     * Returns the hasser name for the field (Ex: hasSingleString).
     *
     * @throws IllegalArgumentException If the message does not have a hasser.
     */
    public String getHasserName() {
      if (!getHasHasser()) {
        throw new IllegalArgumentException("This message does not have a hasser.");
      }
      return "has" + getMethodSuffixName();
    }

    /**
     * Returns the fully qualified java class name for the underlying field. (Ex:
     * com.google.protobuf.StringValue). Returns an empty string for primitives .
     */
    public String getFieldJavaClassName() {
      return fieldJavaClassName;
    }

    public CelFieldValueType getCelFieldValueType() {
      return celFieldValueType;
    }

    /**
     * Gets the field's protobuf type.
     *
     * <p>This is exactly the same as com.google.protobuf.Descriptors#Type
     */
    public Type getProtoFieldType() {
      return protoFieldType;
    }

    public boolean getHasHasser() {
      return hasHasser && celFieldValueType.equals(CelFieldValueType.SCALAR);
    }

    /**
     * Gets the fully qualified protobuf message field name, including its package name (ex:
     * cel.expr.conformance.proto3.TestAllTypes.single_string)
     */
    public String getFullyQualifiedProtoFieldName() {
      return fullyQualifiedProtoFieldName;
    }

    /**
     * Gets the fully qualified protobuf type name for the field, including its package name (ex:
     * cel.expr.conformance.proto3.TestAllTypes.SingleStringWrapper). Returns an empty string for
     * primitives.
     */
    public String getFieldProtoTypeName() {
      return fieldProtoTypeName;
    }

    /**
     * Must be public, used for codegen only. Do not use.
     *
     * @param fullyQualifiedProtoTypeName Fully qualified protobuf type name including the namespace
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
        String fullyQualifiedProtoTypeName,
        String javaTypeName,
        String methodSuffixName,
        String celFieldValueType, // LIST, MAP, SCALAR
        String protoFieldType, // INT32, SINT32, GROUP, MESSAGE... (See Descriptors#Type)
        String hasHasser, //
        String fieldJavaClassName,
        String fieldProtoTypeName) {
      this.fullyQualifiedProtoFieldName = checkNotNull(fullyQualifiedProtoTypeName);
      this.javaType = JavaType.valueOf(javaTypeName);
      this.methodSuffixName = checkNotNull(methodSuffixName);
      this.fieldJavaClassName = checkNotNull(fieldJavaClassName);
      this.celFieldValueType = CelFieldValueType.valueOf(checkNotNull(celFieldValueType));
      this.protoFieldType = Type.valueOf(protoFieldType);
      this.hasHasser = Boolean.parseBoolean(hasHasser);
      this.fieldProtoTypeName = checkNotNull(fieldProtoTypeName);
      this.fieldJavaClass = getPrimitiveFieldTypeClass();
    }

    @SuppressWarnings("ReturnMissingNullable") // Avoid taking a dependency on jspecify.nullable.
    private Class<?> getPrimitiveFieldTypeClass() {
      switch (celFieldValueType) {
        case LIST:
          return Iterable.class;
        case MAP:
          return Map.class;
        case SCALAR:
          return getScalarFieldTypeClass();
      }

      throw new IllegalStateException("Unexpected celFieldValueType: " + celFieldValueType);
    }

    @SuppressWarnings("ReturnMissingNullable") // Avoid taking a dependency on jspecify.nullable.
    private Class<?> getScalarFieldTypeClass() {
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

    private Class<?> loadNonPrimitiveFieldTypeClass() {
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

  protected CelLiteDescriptor(List<MessageLiteDescriptor> messageInfoList) {
    Map<String, MessageLiteDescriptor> protoFqnMap =
        new HashMap<>(getMapInitialCapacity(messageInfoList.size()));
    Map<Class<?>, MessageLiteDescriptor> protoJavaClassNameMap =
        new HashMap<>(getMapInitialCapacity(messageInfoList.size()));
    for (MessageLiteDescriptor msgInfo : messageInfoList) {
      protoFqnMap.put(msgInfo.getProtoTypeName(), msgInfo);
      protoJavaClassNameMap.put(msgInfo.clazz, msgInfo);
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

  @CanIgnoreReturnValue
  private static <T> T checkNotNull(T reference) {
    if (reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }
}
