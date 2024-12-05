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

import dev.cel.expr.Type;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.common.types.CelType;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The {@code TypeProvider} defines methods to lookup types and enums, and resolve field types.
 *
 * @deprecated Use {@code CelTypeProvider} instead.
 */
@Deprecated
public interface TypeProvider {

  /** Lookup the a {@link Type} given a qualified {@code typeName}. Returns null if not found. */
  @Nullable Type lookupType(String typeName);

  /** Lookup the a {@link CelType} given a qualified {@code typeName}. Returns null if not found. */
  default Optional<CelType> lookupCelType(String typeName) {
    Type type = lookupType(typeName);
    return Optional.ofNullable(type).map(CelProtoTypes::typeToCelType);
  }

  /** Lookup the {@code Integer} enum value given an {@code enumName}. Returns null if not found. */
  @Nullable Integer lookupEnumValue(String enumName);

  /**
   * Lookup the {@code FieldType} for a {@code fieldName} within a {@code type}. Returns null if not
   * found.
   *
   * <p>The {@code FieldType} return value will indicate the type of the field and whether presence
   * check is supported via the ('has') macro.
   */
  @Nullable FieldType lookupFieldType(Type type, String fieldName);

  /**
   * Lookup the {@code FieldType} for a {@code fieldName} within a {@code type}. Returns null if not
   * found.
   *
   * <p>The {@code FieldType} return value will indicate the type of the field and whether presence
   * check is supported via the ('has') macro.
   */
  default @Nullable FieldType lookupFieldType(CelType type, String fieldName) {
    return lookupFieldType(CelProtoTypes.celTypeToType(type), fieldName);
  }

  /**
   * Returns the field names associated with the given {@code Type}.
   *
   * <p>If the type is not a message type, or the type is not found, the result is {@code null}.
   */
  default @Nullable ImmutableSet<String> lookupFieldNames(Type type) {
    throw new UnsupportedOperationException("lookupFieldNames is not implemented");
  }

  /**
   * Lookup the {@code FieldType} of the named extension (specified using its full path). Returns
   * null if not found.
   */
  default @Nullable ExtensionFieldType lookupExtensionType(String extensionName) {
    return null; // Default implementation does not find any extensions.
  }

  /** Result of a {@link TypeProvider#lookupFieldType} call. */
  @AutoValue
  public abstract class FieldType {

    /** The {@link Type} of the field. */
    public abstract Type type();

    public CelType celType() {
      return CelProtoTypes.typeToCelType(type());
    }

    /** Create a new {@code FieldType} instance from the provided {@code type}. */
    public static FieldType of(Type type) {
      return new AutoValue_TypeProvider_FieldType(type);
    }
  }

  /** Result of a {@link TypeProvider#lookupExtensionType} call. */
  @AutoValue
  public abstract class ExtensionFieldType {

    /** The {@link FieldType} of the extension field. */
    public abstract FieldType fieldType();

    /** The {@link Type} of the message being extended. */
    public abstract Type messageType();

    public static ExtensionFieldType of(Type fieldType, Type messageType) {
      return new AutoValue_TypeProvider_ExtensionFieldType(FieldType.of(fieldType), messageType);
    }
  }

  /**
   * The {@code CombinedTypeProvider} takes one or more {@code TypeProvider} instances and attempts
   * to look up a {@code Type} instance for a given {@code typeName} by calling each {@code
   * TypeProvider} in the order that they are provided to the constructor.
   */
  final class CombinedTypeProvider implements TypeProvider {

    private final ImmutableList<TypeProvider> typeProviders;

    public CombinedTypeProvider(Iterable<TypeProvider> typeProviders) {
      this.typeProviders = ImmutableList.copyOf(typeProviders);
    }

    @Override
    public @Nullable Type lookupType(String typeName) {
      return findFirstNonNull(typeProvider -> typeProvider.lookupType(typeName));
    }

    @Override
    public @Nullable Integer lookupEnumValue(String enumName) {
      return findFirstNonNull(typeProvider -> typeProvider.lookupEnumValue(enumName));
    }

    @Override
    public @Nullable FieldType lookupFieldType(Type type, String fieldName) {
      return findFirstNonNull(typeProvider -> typeProvider.lookupFieldType(type, fieldName));
    }

    @Override
    public @Nullable ImmutableSet<String> lookupFieldNames(Type type) {
      // The expectation is that only one provider will contain a reference to the provided type.
      return findFirstNonNull(typeProvider -> typeProvider.lookupFieldNames(type));
    }

    @Override
    public @Nullable ExtensionFieldType lookupExtensionType(String extensionName) {
      return findFirstNonNull(typeProvider -> typeProvider.lookupExtensionType(extensionName));
    }

    private <T> @Nullable T findFirstNonNull(Function<TypeProvider, T> lookup) {
      for (TypeProvider typeProvider : typeProviders) {
        T result = lookup.apply(typeProvider);
        if (result != null) {
          return result;
        }
      }
      return null;
    }
  }
}
