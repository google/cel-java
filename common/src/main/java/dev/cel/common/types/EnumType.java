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

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;
import java.util.Optional;

/** Enum types to support strongly typed enums with lookups by value name and number. */
@CheckReturnValue
public final class EnumType extends CelType {

  private final String name;
  private final EnumNumberResolver enumNumberResolver;
  private final EnumNameResolver enumNameResolver;

  EnumType(String name, EnumNumberResolver enumNumberResolver, EnumNameResolver enumNameResolver) {
    this.name = name;
    this.enumNumberResolver = enumNumberResolver;
    this.enumNameResolver = enumNameResolver;
  }

  @Override
  public CelKind kind() {
    return CelKind.INT;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public boolean isAssignableFrom(CelType other) {
    return super.isAssignableFrom(other) || other.kind().equals(CelKind.INT);
  }

  /** Find an enum number by {@code enumName}. */
  public Optional<Integer> findNumberByName(String enumName) {
    return enumNumberResolver.findNumber(enumName);
  }

  /** Find an enum name by {@code enumNumber}. */
  public Optional<String> findNameByNumber(Integer enumNumber) {
    return enumNameResolver.findName(enumNumber);
  }

  /** Functional interface for lookup up an enum number by its local or fully qualified name. */
  @Immutable
  @FunctionalInterface
  @SuppressWarnings("AndroidJdkLibsChecker") // FunctionalInterface added in 24
  public interface EnumNumberResolver {
    Optional<Integer> findNumber(String enumName);
  }

  /** Functional interface for looking up an enum name by its number. */
  @Immutable
  @FunctionalInterface
  @SuppressWarnings("AndroidJdkLibsChecker") // FunctionalInterface added in 24
  public interface EnumNameResolver {
    Optional<String> findName(Integer enumNumber);
  }

  public static EnumType create(String name, ImmutableMap<String, Integer> enumNameToNumber) {
    return create(
        name,
        (enumName) -> Optional.ofNullable(enumNameToNumber.get(enumName)),
        (num) ->
            enumNameToNumber.entrySet().stream()
                .filter(entry -> entry.getValue().equals(num))
                .findFirst()
                .map(Map.Entry::getKey));
  }

  public static EnumType create(
      String name, EnumNumberResolver enumNumberResolver, EnumNameResolver enumNameResolver) {
    return new EnumType(name, enumNumberResolver, enumNameResolver);
  }
}
