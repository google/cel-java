// Copyright 2022 Google LLC
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

import dev.cel.expr.Type;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.EnumType;
import dev.cel.common.types.ProtoMessageType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.TypeType;
import java.util.Optional;
import org.jspecify.nullness.Nullable;

/**
 * The {@code TypeProviderLegacyImpl} acts as a bridge between the old and new type provider APIs
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@CheckReturnValue
@Internal
final class TypeProviderLegacyImpl implements TypeProvider {

  private final CelTypeProvider celTypeProvider;

  TypeProviderLegacyImpl(CelTypeProvider celTypeProvider) {
    this.celTypeProvider = celTypeProvider;
  }

  @Override
  public @Nullable Type lookupType(String typeName) {
    return lookupCelType(typeName).map(CelTypes::celTypeToType).orElse(null);
  }

  @Override
  public Optional<CelType> lookupCelType(String typeName) {
    return celTypeProvider.findType(typeName).map(TypeType::create);
  }

  @Override
  public @Nullable FieldType lookupFieldType(CelType type, String fieldName) {
    String messageType = type.name();
    StructType structType =
        (StructType)
            celTypeProvider.findType(messageType).filter(t -> t instanceof StructType).orElse(null);
    if (structType == null) {
      return null;
    }

    return structType
        .findField(fieldName)
        .map(f -> FieldType.of(CelTypes.celTypeToType(f.type())))
        .orElse(null);
  }

  @Override
  public @Nullable FieldType lookupFieldType(Type type, String fieldName) {
    return lookupFieldType(CelTypes.typeToCelType(type), fieldName);
  }

  @Override
  public @Nullable ImmutableSet<String> lookupFieldNames(Type type) {
    String messageType = type.getMessageType();
    return celTypeProvider
        .findType(messageType)
        .filter(t -> t instanceof StructType)
        .map(t -> ((StructType) t).fieldNames())
        .orElse(null);
  }

  @Override
  public @Nullable Integer lookupEnumValue(String enumName) {
    int dotIndex = enumName.lastIndexOf(".");
    if (dotIndex < 0 || dotIndex == enumName.length() - 1) {
      return null;
    }
    String enumTypeName = enumName.substring(0, dotIndex);
    String localEnumName = enumName.substring(dotIndex + 1);
    return celTypeProvider
        .findType(enumTypeName)
        .filter(t -> t instanceof EnumType)
        .flatMap(t -> ((EnumType) t).findNumberByName(localEnumName))
        .orElse(null);
  }

  @Override
  public @Nullable ExtensionFieldType lookupExtensionType(String extensionName) {
    Optional<ProtoMessageType.Extension> extension =
        celTypeProvider.types().stream()
            .filter(t -> t instanceof ProtoMessageType)
            .map(t -> (ProtoMessageType) t)
            .map(t -> t.findExtension(extensionName))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

    return extension
        .map(
            et ->
                ExtensionFieldType.of(
                    CelTypes.celTypeToType(et.type()), CelTypes.celTypeToType(et.messageType())))
        .orElse(null);
  }
}
