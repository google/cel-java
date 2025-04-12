package dev.cel.protobuf;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.annotations.Internal;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.CelFieldValueType;
import org.jspecify.annotations.Nullable;
/**
 * LiteDescriptorCodegenMetadata holds metadata collected from a full protobuf descriptor pertinent for generating a {@link CelLiteDescriptor}.
 *
 * <p>The class properties here are almost identical to CelLiteDescriptor, except it contains extraneous information such as the fully qualified class names to
 * support codegen, which do not need to be present on a CelLiteDescriptor instance.
 *
 * <p>Note: Properties must be of simple primitive types.
 *
 * <p>Note: JavaBeans prefix (e.g: getFoo) is required for compatibility with freemarker.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@AutoValue
@Internal
public abstract class LiteDescriptorCodegenMetadata {

  public abstract String getProtoTypeName();

  public abstract ImmutableList<FieldLiteDescriptorMetadata> getFieldDescriptors();

  public abstract @Nullable String getJavaClassName(); // A java class name is not populated for maps, even though it behaves like a message.

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setProtoTypeName(String protoTypeName);
    abstract Builder setJavaClassName(String javaClassName);

    abstract ImmutableList.Builder<FieldLiteDescriptorMetadata> fieldDescriptorsBuilder();

    @CanIgnoreReturnValue
    Builder addFieldDescriptor(FieldLiteDescriptorMetadata fieldDescriptor) {
      this.fieldDescriptorsBuilder().add(fieldDescriptor);
      return this;
    }

    abstract LiteDescriptorCodegenMetadata build();
  }

  static Builder newBuilder() {
    return new AutoValue_LiteDescriptorCodegenMetadata.Builder();
  }

  @AutoValue
  public abstract static class FieldLiteDescriptorMetadata {

    public abstract int getFieldNumber();

    public abstract String getFieldName();

    // Fully-qualified name to the Java Type enumeration (ex: dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.INT)
    public String getJavaTypeEnumName() {
      return getFullyQualifiedEnumName(getJavaType());
    }

    // Fully-qualified name to the CelFieldValueType enumeration (ex: dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.SCALAR)
    public String getCelFieldValueTypeEnumName() {
      return getFullyQualifiedEnumName(getCelFieldValueType());
    }

    // Fully-qualified name to the Proto Type enumeration (ex: dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.INT)
    public String getProtoFieldTypeEnumName() {
      return getFullyQualifiedEnumName(getProtoFieldType());
    }

    public abstract boolean getHasPresence();

    public abstract boolean getIsPacked();

    public abstract String getFieldProtoTypeName();

    abstract FieldLiteDescriptor.JavaType getJavaType();

    abstract FieldLiteDescriptor.Type getProtoFieldType();

    abstract FieldLiteDescriptor.CelFieldValueType getCelFieldValueType();

    private static String getFullyQualifiedEnumName(Object enumValue) {
      String enumClassName = enumValue.getClass().getName();
      return (enumClassName + "." + enumValue).replaceAll("\\$", ".");
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setFieldNumber(int fieldNumber);
      abstract Builder setFieldName(String fieldName);
      abstract Builder setJavaType(FieldLiteDescriptor.JavaType javaTypeEnum);
      abstract Builder setCelFieldValueType(FieldLiteDescriptor.CelFieldValueType celFieldValueTypeEnum);
      abstract Builder setProtoFieldType(FieldLiteDescriptor.Type protoFieldTypeEnum);
      abstract Builder setHasPresence(boolean hasHasser);
      abstract Builder setIsPacked(boolean isPacked);
      abstract Builder setFieldProtoTypeName(String fieldProtoTypeName);

      abstract FieldLiteDescriptorMetadata build();
    }

    static FieldLiteDescriptorMetadata.Builder newBuilder() {
      return new AutoValue_LiteDescriptorCodegenMetadata_FieldLiteDescriptorMetadata.Builder()
          .setFieldProtoTypeName("");
    }
  }
}
