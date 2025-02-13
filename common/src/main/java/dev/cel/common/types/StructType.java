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

package dev.cel.common.types;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Struct type indicates that the type {@code name} is a structured object with typed properties.
 *
 * <p>The definition of the struct properties occurs outside the CEL type specification.
 */
@CheckReturnValue
@Immutable
public class StructType extends CelType {

  protected final String name;
  protected final ImmutableSet<String> fieldNames;
  protected final FieldResolver fieldResolver;

  StructType(String name, ImmutableSet<String> fieldNames, FieldResolver fieldResolver) {
    this.name = name;
    this.fieldNames = fieldNames;
    this.fieldResolver = fieldResolver;
  }

  @Override
  public CelKind kind() {
    return CelKind.STRUCT;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public boolean isAssignableFrom(CelType other) {
    return super.isAssignableFrom(other)
        || ((other instanceof StructType || other instanceof StructTypeReference)
            && other.name().equals(name()));
  }

  /** Returns the {@code CelType} associated with the struct field name. */
  public Optional<Field> findField(String fieldName) {
    if (!fieldNames.contains(fieldName)) {
      return Optional.empty();
    }
    return fieldResolver.findField(fieldName).map(type -> Field.of(fieldName, type));
  }

  /** Returns the set of declare field names and their types. */
  public ImmutableSet<String> fieldNames() {
    return fieldNames;
  }

  /**
   * Returns the full set of {@code Field} definitions visible within this struct.
   *
   * <p>Note, the struct may support a broader set of fields than is returned; however, the input
   * {@code fieldNames} to constructor limits which fields are visible.
   */
  public ImmutableSet<Field> fields() {
    return fieldNames().stream()
        .map(
            f ->
                findField(f)
                    .orElseThrow(
                        () -> new NoSuchElementException(String.format("no such field: %s", f))))
        .collect(toImmutableSet());
  }

  public static StructType create(
      String name, ImmutableSet<String> fieldNames, FieldResolver fieldResolver) {
    return new StructType(name, fieldNames, fieldResolver);
  }

  /**
   * The {@code FieldResolver} is used to lookup the type of a {@code StructType}'s {@code
   * fieldName}.
   */
  @Immutable
  @FunctionalInterface
  @SuppressWarnings("AndroidJdkLibsChecker") // FunctionalInterface added in 24
  public interface FieldResolver {
    /** Find the {@code CelType} for the given {@code fieldName} if the field is defined. */
    Optional<CelType> findField(String fieldName);
  }

  /** Value object which holds a reference to the field name and type. */
  @Immutable
  @AutoValue
  public abstract static class Field {
    public abstract String name();

    public abstract CelType type();

    public static Field of(String name, CelType type) {
      return new AutoValue_StructType_Field(name, type);
    }
  }
}
