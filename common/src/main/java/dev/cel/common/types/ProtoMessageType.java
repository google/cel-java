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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/**
 * The {@code ProtoMessageType} is a {@code StructType} with support for proto {@code Extension}s
 * and field masks.
 */
@CheckReturnValue
@Immutable
public final class ProtoMessageType extends StructType {

  private final StructType.FieldResolver extensionResolver;
  private final JsonNameResolver jsonNameResolver;

  ProtoMessageType(
      String name,
      ImmutableSet<String> fieldNames,
      StructType.FieldResolver fieldResolver,
      StructType.FieldResolver extensionResolver,
      JsonNameResolver jsonNameResolver) {
    super(name, fieldNames, fieldResolver);
    this.extensionResolver = extensionResolver;
    this.jsonNameResolver = jsonNameResolver;
  }

  /** Find an {@code Extension} by its fully-qualified {@code extensionName}. */
  public Optional<Extension> findExtension(String extensionName) {
    return extensionResolver
        .findField(extensionName)
        .map(type -> Extension.of(extensionName, type, this));
  }

  /** Returns true if the field name is a json name. */
  public boolean isJsonName(String fieldName) {
    return jsonNameResolver.isJsonName(fieldName);
  }

  /**
   * Create a new instance of the {@code ProtoMessageType} using the {@code visibleFields} set as a
   * mask of the fields from the backing proto.
   */
  public ProtoMessageType withVisibleFields(ImmutableSet<String> visibleFields) {
    return new ProtoMessageType(
        name, visibleFields, fieldResolver, extensionResolver, jsonNameResolver);
  }

  public static ProtoMessageType create(
      String name,
      ImmutableSet<String> fieldNames,
      FieldResolver fieldResolver,
      FieldResolver extensionResolver,
      JsonNameResolver jsonNameResolver) {
    return new ProtoMessageType(
        name, fieldNames, fieldResolver, extensionResolver, jsonNameResolver);
  }

  /** Functional interface for resolving whether a field name is a json name. */
  @FunctionalInterface
  @Immutable
  public interface JsonNameResolver {
    boolean isJsonName(String fieldName);
  }

  /** {@code Extension} contains the name, type, and target message type of the extension. */
  @Immutable
  @AutoValue
  public abstract static class Extension {
    public abstract String name();

    public abstract CelType type();

    public abstract CelType messageType();

    static Extension of(String name, CelType type, CelType messageType) {
      return new AutoValue_ProtoMessageType_Extension(name, type, messageType);
    }
  }
}
