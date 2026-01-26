// Copyright 2023 Google LLC
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

package dev.cel.checker;

import com.google.common.collect.ImmutableCollection;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ProtoMessageType;
import dev.cel.common.types.TypeType;
import dev.cel.expr.Type;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.FileDescriptorSetConverter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The {@code DescriptorTypeProvider} provides type information for one or more {@link Descriptor}
 * instances of proto messages.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
@Deprecated
public class DescriptorTypeProvider implements TypeProvider {

  @SuppressWarnings("Immutable")
  private final SymbolTable symbolTable;

  /** Constructs the empty provider, which will resolve nothing. */
  public DescriptorTypeProvider() {
    this(new ArrayList<FileDescriptor>());
  }

  /**
   * Constructs a provider based on the given file descriptor set proto, as it is emitted by the
   * protocol compiler.
   */
  public DescriptorTypeProvider(FileDescriptorSet descriptorSet) {
    this(FileDescriptorSetConverter.convert(descriptorSet));
  }

  public DescriptorTypeProvider(Collection<FileDescriptor> fileDescriptors) {
    this.symbolTable = new SymbolTable(ImmutableList.copyOf(fileDescriptors));
  }

  public DescriptorTypeProvider(Iterable<Descriptor> descriptors) {
    this.symbolTable = new SymbolTable(ImmutableList.copyOf(descriptors));
  }

  @Override
  public @Nullable Type lookupType(String typeName) {
    TypeDef typeDef = lookupMessageTypeDef(typeName);
    return typeDef != null ? Types.create(Types.createMessage(typeDef.name())) : null;
  }

  @Override
  public Optional<TypeType> lookupCelType(String typeName) {
    TypeDef typeDef = lookupMessageTypeDef(typeName);
    if (typeDef == null) {
      return Optional.empty();
    }

    ImmutableSet.Builder<String> fieldsBuilder = ImmutableSet.builder();
    for (FieldDef fieldDef : typeDef.fields()) {
      fieldsBuilder.add(fieldDef.name());
    }

    @SuppressWarnings("Immutable") // Legacy type defs
    ProtoMessageType protoMessageType = ProtoMessageType.create(
        typeName,
        fieldsBuilder.build(),
        fieldName -> {
          FieldDef fieldDef = typeDef.lookupField(fieldName);
          if (fieldDef == null) {
            return Optional.empty();
          }

          Type type = fieldDefToType(fieldDef);
          return Optional.of(CelProtoTypes.typeToCelType(type));
        },
        extensionFieldName -> {
          ExtensionFieldType extensionFieldType = symbolTable.lookupExtension(extensionFieldName);
          if (extensionFieldType == null) {
            return Optional.empty();
          }

          return Optional.of(extensionFieldType.fieldType().celType());
        }
      );

    return Optional.of(TypeType.create(protoMessageType));
  }
  @Override
  public @Nullable Integer lookupEnumValue(String enumName) {
    int dot = enumName.lastIndexOf('.');
    if (dot > 0) {
      String enumTypeName = enumName.substring(0, dot);
      String enumValueName = enumName.substring(dot + 1);
      TypeDef typeDef = symbolTable.lookupTypeDef(enumTypeName);
      if (typeDef != null && typeDef.isEnum()) {
        EnumValueDef enumValueDef = typeDef.findEnumValue(enumValueName);
        if (enumValueDef != null) {
          return enumValueDef.value();
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable FieldType lookupFieldType(Type type, String fieldName) {
    TypeDef messageTypeDef = lookupMessageTypeDef(type.getMessageType());
    if (messageTypeDef == null) {
      return null;
    }
    FieldDef fieldDef = messageTypeDef.lookupField(fieldName);
    if (fieldDef == null) {
      return null;
    }
    return FieldType.of(fieldDefToType(fieldDef));
  }

  @Override
  public @Nullable ImmutableSet<String> lookupFieldNames(Type type) {
    if (type.getTypeKindCase() != Type.TypeKindCase.MESSAGE_TYPE) {
      return null;
    }
    TypeDef messageTypeDef = lookupMessageTypeDef(type.getMessageType());
    if (messageTypeDef == null) {
      return null;
    }
    ImmutableSet.Builder<String> fields = ImmutableSet.builder();
    for (FieldDef fieldDef : messageTypeDef.fields()) {
      fields.add(fieldDef.name());
    }
    return fields.build();
  }

  @Override
  public @Nullable ExtensionFieldType lookupExtensionType(String extensionName) {
    return symbolTable.lookupExtension(extensionName);
  }

  private @Nullable TypeDef lookupMessageTypeDef(String typeName) {
    TypeDef typeDef = symbolTable.lookupTypeDef(typeName);
    return (typeDef != null && typeDef.isMessage()) ? typeDef : null;
  }

  private static Type fieldDefToType(FieldDef fieldDef) {
    if (fieldDef.isMap()) {
      return Types.createMap(
          typeDefToType(fieldDef.mapEntryType().keyType()),
          typeDefToType(fieldDef.mapEntryType().valueType()));
    }
    if (fieldDef.repeated()) {
      return Types.createList(typeDefToType(fieldDef.type()));
    }
    return typeDefToType(fieldDef.type());
  }

  private static Type typeDefToType(TypeDef typeDef) {
    if (typeDef.isMessage()) {
      if (Types.WELL_KNOWN_TYPE_MAP.containsKey(typeDef.name())) {
        return Types.WELL_KNOWN_TYPE_MAP.get(typeDef.name());
      }
      return Types.createMessage(typeDef.name());
    }
    if (Types.PRIMITIVE_TYPE_MAP.containsKey(typeDef.protoType())) {
      return Types.PRIMITIVE_TYPE_MAP.get(typeDef.protoType());
    }
    if (typeDef.isEnum()) {
      return Types.INT64;
    }
    throw new IllegalArgumentException("unexpected typeDef: " + typeDef);
  }

  /**
   * Helper class to construct a symbol table for messages, enums, and their respective
   * declarations.
   *
   * <p>The symbol table is built from the eagerly and is not a pure representation of Proto, but
   * rather just enough of one to serve the needs of CEL.
   */
  protected static class SymbolTable {

    private final ImmutableMap<String, TypeDef> typeMap;
    private final ImmutableMap<String, ExtensionFieldType> extensionMap;

    public SymbolTable(Iterable<FileDescriptor> fileDescriptors) {
      Set<String> processedFiles = new HashSet<>();
      Map<String, TypeDef> typeMap = new HashMap<>();
      Map<String, ExtensionFieldType> extensionMap = new HashMap<>();
      for (FileDescriptor fileDescriptor : fileDescriptors) {
        // Message descriptors may depend on enums present within the file where they are declared.
        // Be certain to process the file descriptor holding the message rather than just the
        // descriptor for the message.
        if (processedFiles.add(fileDescriptor.getFullName())) {
          for (Descriptor messageDescriptor : fileDescriptor.getMessageTypes()) {
            buildTypeDef(messageDescriptor, typeMap);
          }
          for (EnumDescriptor enumDescriptor : fileDescriptor.getEnumTypes()) {
            buildTypeDef(enumDescriptor, typeMap);
          }
          for (FieldDescriptor extensionDescriptor : fileDescriptor.getExtensions()) {
            FieldDef field = buildFieldDef(extensionDescriptor, typeMap);
            if (field != null) {
              extensionMap.put(
                  extensionDescriptor.getFullName(),
                  ExtensionFieldType.of(
                      fieldDefToType(field),
                      Types.createMessage(extensionDescriptor.getContainingType().getFullName())));
            }
          }
        }
      }
      this.typeMap = ImmutableMap.copyOf(typeMap);
      this.extensionMap = ImmutableMap.copyOf(extensionMap);
    }

    public SymbolTable(List<Descriptor> descriptors) {
      this(Iterables.transform(descriptors, Descriptor::getFile));
    }

    /** Find a {@link TypeDef} by qualified {@code typeName}. */
    private @Nullable TypeDef lookupTypeDef(String typeName) {
      // In proto-land it is common to prefix the type-name with a dot. Generally, the dot is
      // stripped prior to type resolution.
      typeName = typeName.startsWith(".") ? typeName.substring(1) : typeName;
      return typeMap.get(typeName);
    }

    private @Nullable ExtensionFieldType lookupExtension(String extensionName) {
      extensionName = extensionName.startsWith(".") ? extensionName.substring(1) : extensionName;
      return extensionMap.get(extensionName);
    }

    /** Build a message {@link TypeDef}. */
    @CanIgnoreReturnValue
    private TypeDef buildTypeDef(Descriptor descriptor, Map<String, TypeDef> typeMap) {
      // If the type has already been resolved, return it. This should account for fields which
      // refer to the type of the message being defined.
      String typeName = descriptor.getFullName();
      if (typeMap.containsKey(typeName)) {
        return typeMap.get(typeName);
      }

      // Initialize the type entry since types can be nested.
      Set<FieldDef> fields = new HashSet<>();
      TypeDef typeDef = TypeDef.ofMessage(typeName, fields);
      typeMap.put(typeName, typeDef);

      // Build the type definitions for nested types and enums.
      for (EnumDescriptor enumDescriptor : descriptor.getEnumTypes()) {
        buildTypeDef(enumDescriptor, typeMap);
      }
      for (Descriptor nested : descriptor.getNestedTypes()) {
        buildTypeDef(nested, typeMap);
      }

      // Populate the typeDef fields set from fields and one-of fields.
      // This is somewhat dirty in that it modifies a value within the typeMap; however,
      // unavoidable due to the recursive nature of proto field definitions.
      for (FieldDescriptor fieldDescriptor : descriptor.getFields()) {
        FieldDef field = buildFieldDef(fieldDescriptor, typeMap);
        if (field != null) {
          fields.add(field);
        }
      }
      for (OneofDescriptor oneof : descriptor.getOneofs()) {
        for (FieldDescriptor oneofFieldDescriptor : oneof.getFields()) {
          FieldDef field = buildFieldDef(oneofFieldDescriptor, typeMap);
          if (field != null) {
            fields.add(field);
          }
        }
      }
      return typeDef;
    }

    /** Build an enum {@link TypeDef}. */
    @CanIgnoreReturnValue
    private TypeDef buildTypeDef(EnumDescriptor descriptor, Map<String, TypeDef> typeMap) {
      String typeName = descriptor.getFullName();
      if (typeMap.containsKey(typeName)) {
        return typeMap.get(typeName);
      }
      Set<EnumValueDef> enumValues = new HashSet<>();
      //
      TypeDef typeDef = TypeDef.ofEnum(typeName, enumValues);
      typeMap.put(typeName, typeDef);
      for (EnumValueDescriptor enumValue : descriptor.getValues()) {
        enumValues.add(EnumValueDef.of(enumValue.getName(), enumValue.getNumber()));
      }
      return typeDef;
    }

    /** Build a {@link FieldDef}. Null if the field type is not-supported, i.e. proto groups. */
    private @Nullable FieldDef buildFieldDef(
        FieldDescriptor fieldDescriptor, Map<String, TypeDef> typeMap) {
      String fieldName = fieldDescriptor.getName();
      boolean repeated = fieldDescriptor.isRepeated();
      switch (fieldDescriptor.getType()) {
        case GROUP:
        case MESSAGE:
          if (fieldDescriptor.isMapField()) {
            FieldDef keyField =
                buildFieldDef(fieldDescriptor.getMessageType().getFields().get(0), typeMap);
            FieldDef valueField =
                buildFieldDef(fieldDescriptor.getMessageType().getFields().get(1), typeMap);
            if (keyField != null && valueField != null) {
              return FieldDef.of(fieldName, MapEntryDef.of(keyField.type(), valueField.type()));
            }
            // Either the key or field was an unsupported field type.
            return null;
          }
          TypeDef messageDef = buildTypeDef(fieldDescriptor.getMessageType(), typeMap);
          return FieldDef.of(fieldName, messageDef, repeated);
        case ENUM:
          TypeDef enumDef = buildTypeDef(fieldDescriptor.getEnumType(), typeMap);
          return FieldDef.of(fieldName, enumDef, repeated);
        case DOUBLE:
        case FLOAT:
        case FIXED32:
        case FIXED64:
        case INT32:
        case INT64:
        case SINT32:
        case SINT64:
        case SFIXED32:
        case SFIXED64:
        case UINT32:
        case UINT64:
        case BOOL:
        case STRING:
        case BYTES:
          TypeDef primitiveDef = TypeDef.of(fieldDescriptor.toProto().getType());
          return FieldDef.of(fieldName, primitiveDef, repeated);
      }
      // Unsupported types yield a null type resolution.
      return null;
    }
  }

  /** Value object for a proto-based primitive, message, or enum definition. */
  @AutoValue
  @AutoValue.CopyAnnotations
  @SuppressWarnings("Immutable")
  protected abstract static class TypeDef {

    /** The qualified name of the message or enum. */
    public abstract String name();

    /** The proto-based type enum for the type. */
    public abstract FieldDescriptorProto.Type protoType();

    /**
     * The set of {@link FieldDef} values for a message. Non-null when {@link #isMessage} is true.
     */
    public abstract @Nullable Iterable<FieldDef> fields();

    /**
     * The set of {@link EnumValueDef} values for an enum. Non-null when {@link #isEnum} is true.
     */
    public abstract @Nullable Iterable<EnumValueDef> enumValues();

    /** Return whether the type is an enum. */
    public boolean isEnum() {
      return enumValues() != null;
    }

    /** Return whether the type is a message. */
    public boolean isMessage() {
      return fields() != null;
    }

    public @Nullable FieldDef lookupField(String name) {
      for (FieldDef field : fields()) {
        if (field.name().equals(name)) {
          return field;
        }
      }
      return null;
    }

    public @Nullable EnumValueDef findEnumValue(String name) {
      for (EnumValueDef enumValue : enumValues()) {
        if (enumValue.name().equals(name)) {
          return enumValue;
        }
      }
      return null;
    }

    @Override
    public final boolean equals(Object other) {
      // override the equality behavior as the recursive nature of proto fields can cause a stack
      // overflow.
      return other instanceof TypeDef && ((TypeDef) other).name().equals(name());
    }

    @Override
    public final int hashCode() {
      // override the hashcode to match the equality behavior.
      return name().hashCode();
    }

    @Override
    public final String toString() {
      Joiner joiner = Joiner.on("\n");
      return String.format(
          "%s {\n%s\n}",
          name(),
          isMessage()
              ? joiner.join(fields())
              : isEnum() ? joiner.join(enumValues()) : Ascii.toLowerCase(protoType().name()));
    }

    /** Create a {@code TypeDef} for a primitive type. */
    public static TypeDef of(FieldDescriptorProto.Type protoType) {
      return new AutoValue_DescriptorTypeProvider_TypeDef(
          Ascii.toLowerCase(protoType.name()), protoType, null, null);
    }

    /**
     * Create a {@code TypeDef} for a message type given the qualified message {@code name} in which
     * it was written, and the {@code fields} declared within it.
     */
    static TypeDef ofMessage(String name, Iterable<FieldDef> fields) {
      return new AutoValue_DescriptorTypeProvider_TypeDef(
          name, FieldDescriptorProto.Type.TYPE_MESSAGE, fields, null);
    }

    /**
     * Create a {@code TypeDef} for an enum type given the qualified enum {@code name}, in which it
     * was written, and the {@code enumValues} declared within it.
     */
    static TypeDef ofEnum(String name, Iterable<EnumValueDef> enumValues) {
      return new AutoValue_DescriptorTypeProvider_TypeDef(
          name, FieldDescriptorProto.Type.TYPE_ENUM, null, enumValues);
    }
  }

  @Override
  public ImmutableCollection<CelType> types() {
    ImmutableList.Builder<CelType> typesBuilder = ImmutableList.builder();
    for (TypeDef typeDef : symbolTable.typeMap.values()) {
      TypeType typeType = lookupCelType(typeDef.name()).orElse(null);
      if (typeType == null) {
        continue;
      }

      typesBuilder.add(typeType.type());
    }

    return typesBuilder.build();
  }

  /**
   * Value object for a proto-based field definition.
   *
   * <p>Only one of the {@link #type} or {@link #mapEntryType} may be set.
   */
  @AutoValue
  @AutoValue.CopyAnnotations
  protected abstract static class FieldDef {

    /** The field name. */
    public abstract String name();

    /** The field {@code TypeDef}. Null if {@link #isMap} is true. */
    public abstract @Nullable TypeDef type();

    /** The field {@code MapEntryDef}. Null if {@link #isMap} is false. */
    public abstract @Nullable MapEntryDef mapEntryType();

    /** The field is repeated if it is a list or map type. */
    public abstract boolean repeated();

    /** The field is a map if it has a non-null {@link #mapEntryType}. */
    public boolean isMap() {
      return mapEntryType() != null;
    }

    @Override
    public final boolean equals(Object other) {
      // Override equality testing as the nested nature of protos can cause a stack overflow here.
      return other instanceof FieldDef && ((FieldDef) other).name().equals(name());
    }

    @Override
    public final int hashCode() {
      // Override hashCode to map to the equality constraint.
      return name().hashCode();
    }

    @Override
    public final String toString() {
      return String.format(
          "%s %s;",
          isMap()
              ? mapEntryType().toString()
              : repeated() ? "repeated " + type().name() : type().name(),
          name());
    }

    /**
     * Create a {@code FieldDef} for a non-map field with a {@code name} and {@code type} which may
     * be {@code repeated}/
     */
    static FieldDef of(String name, TypeDef type, boolean repeated) {
      return new AutoValue_DescriptorTypeProvider_FieldDef(name, type, null, repeated);
    }

    /** Create a {@code FieldDef} for a field with a {@code name} and {@code mapEntryType}. */
    static FieldDef of(String name, MapEntryDef mapEntryType) {
      return new AutoValue_DescriptorTypeProvider_FieldDef(name, null, mapEntryType, true);
    }
  }

  /** Value object for an enum {@link #name} and {@link #value}. */
  @AutoValue
  protected abstract static class EnumValueDef {

    /** The simple name of the enum value within a containing enum {@code TypeDef}. */
    public abstract String name();

    /** The numeric value of the enum value. */
    public abstract int value();

    @Override
    public final String toString() {
      return String.format("%s = %d", Ascii.toUpperCase(name()), value());
    }

    /** Construct a {@ EnumValueDef} from a {@code name} and numeric {@code value}. */
    static EnumValueDef of(String name, int value) {
      return new AutoValue_DescriptorTypeProvider_EnumValueDef(name, value);
    }
  }

  /** Value object for Map entry {@code TypeDef} information. */
  @AutoValue
  protected abstract static class MapEntryDef {

    /** The {@code TypeDef} of the map entry key. */
    public abstract TypeDef keyType();

    /** The {@code TypeDef} of the map entry value. */
    public abstract TypeDef valueType();

    @Override
    public final String toString() {
      return String.format("map<%s, %s>", keyType().name(), valueType().name());
    }

    /** Construct a {@code MapEntryDef} from a {@code keyType} and {@code valueType}. */
    static MapEntryDef of(TypeDef keyType, TypeDef valueType) {
      return new AutoValue_DescriptorTypeProvider_MapEntryDef(keyType, valueType);
    }
  }
}
