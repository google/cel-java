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
import com.google.protobuf.MessageLite;
import dev.cel.common.annotations.Internal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

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
  private final String version;

  public Map<String, MessageLiteDescriptor> getProtoTypeNamesToDescriptors() {
    return protoFqnToDescriptors;
  }

  /**
   * Retrieves the CEL-Java version this descriptor was generated with
   */
  public String getVersion() {
    return version;
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

    @SuppressWarnings("Immutable") // Copied to an unmodifiable list
    private final List<FieldLiteDescriptor> fieldLiteDescriptors;

    @SuppressWarnings("Immutable") // Copied to an unmodifiable map
    private final Map<String, FieldLiteDescriptor> fieldNameToFieldDescriptors;

    @SuppressWarnings("Immutable") // Copied to an unmodifiable map
    private final Map<Integer, FieldLiteDescriptor> fieldNumberToFieldDescriptors;

    @SuppressWarnings("Immutable") // Does not alter the descriptor content
    private final Supplier<MessageLite.Builder> messageBuilderSupplier;
    private final String fullyQualifiedJavaClassName;

    public String getProtoTypeName() {
      return fullyQualifiedProtoTypeName;
    }

    public List<FieldLiteDescriptor> getFieldDescriptors() {
      return fieldLiteDescriptors;
    }

    public FieldLiteDescriptor getByFieldNameOrThrow(String fieldName) {
      return Objects.requireNonNull(fieldNameToFieldDescriptors.get(fieldName));
    }

    public FieldLiteDescriptor getByFieldNumberOrThrow(int fieldNumber) {
      return Objects.requireNonNull(fieldNumberToFieldDescriptors.get(fieldNumber));
    }

    public Map<String, FieldLiteDescriptor> getFieldDescriptorsMap() {
      return fieldNameToFieldDescriptors;
    }

    public String getFullyQualifiedJavaClassName() {
      return fullyQualifiedJavaClassName;
    }

    public MessageLite.Builder newMessageBuilder() {
      return messageBuilderSupplier.get();
    }

    @Internal
    MessageLiteDescriptor(
        String fullyQualifiedProtoTypeName,
        List<FieldLiteDescriptor> fieldLiteDescriptors,
        String fullyQualifiedJavaClassName) {
      this(fullyQualifiedProtoTypeName, fieldLiteDescriptors, () -> null, fullyQualifiedJavaClassName);
    }

    /**
     * CEL Library Internals. Do not use.
     *
     * <p>Public visibility due to codegen.
     */
    @Internal
    public MessageLiteDescriptor(
        String fullyQualifiedProtoTypeName,
        List<FieldLiteDescriptor> fieldLiteDescriptors) {
      this(fullyQualifiedProtoTypeName, fieldLiteDescriptors, () -> null, null);
    }

    /**
     * CEL Library Internals. Do not use.
     *
     * <p>Public visibility due to codegen.
     */
    @Internal
    public MessageLiteDescriptor(
        String fullyQualifiedProtoTypeName,
        List<FieldLiteDescriptor> fieldLiteDescriptors,
        Supplier<MessageLite.Builder> messageBuilderSupplier) {
      this(fullyQualifiedProtoTypeName, fieldLiteDescriptors, messageBuilderSupplier, null);
    }

    /**
     * CEL Library Internals. Do not use.
     *
     * <p>Public visibility due to codegen.
     */
    @Internal
    private MessageLiteDescriptor(
        String fullyQualifiedProtoTypeName,
        List<FieldLiteDescriptor> fieldLiteDescriptors,
        Supplier<MessageLite.Builder> messageBuilderSupplier,
        String fullyQualifiedJavaClassName) {
      this.fullyQualifiedProtoTypeName = checkNotNull(fullyQualifiedProtoTypeName);
      // This is a cheap operation. View over the existing map with mutators disabled.
      this.fieldLiteDescriptors = Collections.unmodifiableList(checkNotNull(fieldLiteDescriptors));
      Map<String, FieldLiteDescriptor> fieldNameMap = new HashMap<>(getMapInitialCapacity(
          fieldLiteDescriptors.size()));
      Map<Integer, FieldLiteDescriptor> fieldNumberMap = new HashMap<>(getMapInitialCapacity(
          fieldLiteDescriptors.size()));
      for (FieldLiteDescriptor fd : fieldLiteDescriptors) {
        fieldNameMap.put(fd.fieldName, fd);
        fieldNumberMap.put(fd.fieldNumber, fd);
      }
      this.fieldNameToFieldDescriptors = Collections.unmodifiableMap(fieldNameMap);
      this.fieldNumberToFieldDescriptors = Collections.unmodifiableMap(fieldNumberMap);
      this.messageBuilderSupplier = messageBuilderSupplier;
      this.fullyQualifiedJavaClassName = fullyQualifiedJavaClassName;
    }
  }

  /**
   * Describes a field of a protobuf messagelite type.
   *
   * <p>CEL Library Internals. Do Not Use.
   */
  @Internal
  @Immutable
  public static final class FieldLiteDescriptor {
    private final int fieldNumber;
    private final String fieldName;
    private final JavaType javaType;
    private final String fieldProtoTypeName;
    private final Type protoFieldType;
    private final CelFieldValueType celFieldValueType;
    private final boolean hasHasser;
    private final boolean isPacked;

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

    public int getFieldNumber() {
      return fieldNumber;
    }

    public String getFieldName() {
      return fieldName;
    }

    /**
     * Gets the field's java type.
     *
     * <p>This is exactly the same as com.google.protobuf.Descriptors#JavaType
     */
    public JavaType getJavaType() {
      return javaType;
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

    /**
     * Checks whether the field contains a hasser method (i.e: wrappers).
     */
    public boolean getHasHasser() {
      return hasHasser && celFieldValueType.equals(CelFieldValueType.SCALAR);
    }

    /**
     * Checks whether the repeated field is packed.
     */
    public boolean getIsPacked() {
      return isPacked;
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
     * @param fieldNumber Field index
     * @param fieldName Name of the field
     * @param javaType Canonical Java type name (ex: Long, Double, Float, Message... see
     *     com.google.protobuf.Descriptors#JavaType)
     * @param celFieldValueType Describes whether the field is a scalar, list or a map with respect
     *     to CEL.
     * @param protoFieldType Protobuf Field Type (ex: INT32, SINT32, GROUP, MESSAGE... see
     *     com.google.protobuf.Descriptors#Type)
     * @param hasHasser True if the message has a presence test method (ex: wrappers).
     * @param fieldProtoTypeName Fully qualified protobuf type name for the field. Empty if the
     *     field is a primitive.
     */
    @Internal
    public FieldLiteDescriptor(
        int fieldNumber,
        String fieldName,
        String javaType,
        String celFieldValueType, // LIST, MAP, SCALAR
        String protoFieldType, // INT32, SINT32, GROUP, MESSAGE... (See Descriptors#Type)
        boolean hasHasser,
        boolean isPacked,
        String fieldProtoTypeName) {
      this.fieldNumber = fieldNumber;
      this.fieldName = checkNotNull(fieldName);
      this.javaType = JavaType.valueOf(javaType);
      this.celFieldValueType = CelFieldValueType.valueOf(checkNotNull(celFieldValueType));
      this.protoFieldType = Type.valueOf(protoFieldType);
      this.hasHasser = hasHasser;
      this.isPacked = isPacked;
      this.fieldProtoTypeName = checkNotNull(fieldProtoTypeName);
    }
  }

  protected CelLiteDescriptor(
      String version,
      List<MessageLiteDescriptor> messageInfoList) {
    Map<String, MessageLiteDescriptor> protoFqnMap =
        new HashMap<>(getMapInitialCapacity(messageInfoList.size()));
    for (MessageLiteDescriptor msgInfo : messageInfoList) {
      protoFqnMap.put(msgInfo.getProtoTypeName(), msgInfo);
    }

    this.version = version;
    this.protoFqnToDescriptors = Collections.unmodifiableMap(protoFqnMap);
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
