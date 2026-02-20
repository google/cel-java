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

package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.EnumType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.GlobalResolver;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;

@Immutable
final class NamespacedAttribute implements Attribute {
  private final boolean disambiguateNames;
  private final ImmutableSet<String> namespacedNames;
  private final ImmutableList<Qualifier> qualifiers;
  private final CelValueConverter celValueConverter;
  private final CelTypeProvider typeProvider;

  ImmutableList<Qualifier> qualifiers() {
    return qualifiers;
  }

  ImmutableSet<String> candidateVariableNames() {
    return namespacedNames;
  }

  @Override
  public Object resolve(GlobalResolver ctx, ExecutionFrame frame) {
    GlobalResolver inputVars = ctx;
    // Unwrap any local activations to ensure that we reach the variables provided as input
    // to the expression in the event that we need to disambiguate between global and local
    // variables.
    if (disambiguateNames) {
      inputVars = unwrapToNonLocal(ctx);
    }

    for (String name : namespacedNames) {
      GlobalResolver resolver = ctx;
      if (disambiguateNames) {
        resolver = inputVars;
      }

      Object value = resolver.resolve(name);
      if (value != null) {
        if (!qualifiers.isEmpty()) {
          return applyQualifiers(value, celValueConverter, qualifiers);
        } else {
          return value;
        }
      }

      // Attempt to resolve the qualify type name if the name is not a variable identifier
      value = findIdent(name);
      if (value != null) {
        return value;
      }
    }

    return MissingAttribute.newMissingAttribute(namespacedNames);
  }

  private @Nullable Object findIdent(String name) {
    CelType type = typeProvider.findType(name).orElse(null);
    // If the name resolves directly, this is a fully qualified type name
    // (ex: 'int' or 'google.protobuf.Timestamp')
    if (type != null) {
      if (qualifiers.isEmpty()) {
        // Resolution of a fully qualified type name: foo.bar.baz
        return TypeType.create(type);
      }

      throw new IllegalStateException(
          "Unexpected type resolution when there were remaining qualifiers: " + type.name());
    }

    // The name itself could be a fully qualified reference to an enum value
    // (e.g: my.enum_type.BAR)
    int lastDotIndex = name.lastIndexOf('.');
    if (lastDotIndex > 0) {
      String enumTypeName = name.substring(0, lastDotIndex);
      String enumValueQualifier = name.substring(lastDotIndex + 1);

      return typeProvider
          .findType(enumTypeName)
          .filter(EnumType.class::isInstance)
          .map(EnumType.class::cast)
          .map(enumType -> getEnumValue(enumType, enumValueQualifier))
          .orElse(null);
    }

    return null;
  }

  private static Long getEnumValue(EnumType enumType, String field) {
    return enumType
        .findNumberByName(field)
        .map(Integer::longValue)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    String.format("Field %s was not found on enum %s", enumType.name(), field)));
  }

  private GlobalResolver unwrapToNonLocal(GlobalResolver resolver) {
    while (resolver instanceof ActivationWrapper) {
      resolver = ((ActivationWrapper) resolver).unwrap();
    }
    return resolver;
  }

  @Override
  public NamespacedAttribute addQualifier(Qualifier qualifier) {
    return new NamespacedAttribute(
        typeProvider,
        celValueConverter,
        namespacedNames,
        disambiguateNames,
        ImmutableList.<Qualifier>builder().addAll(qualifiers).add(qualifier).build());
  }

  private static Object applyQualifiers(
      Object value, CelValueConverter celValueConverter, ImmutableList<Qualifier> qualifiers) {
    Object obj = celValueConverter.toRuntimeValue(value);

    for (Qualifier qualifier : qualifiers) {
      obj = qualifier.qualify(obj);
    }

    if (obj instanceof CelValue) {
      obj = celValueConverter.unwrap((CelValue) obj);
    }

    return obj;
  }

  static NamespacedAttribute create(
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter,
      ImmutableSet<String> namespacedNames) {
    ImmutableSet.Builder<String> namesBuilder = ImmutableSet.builder();
    boolean disambiguateNames = false;
    for (String name : namespacedNames) {
      if (name.startsWith(".")) {
        disambiguateNames = true;
        namesBuilder.add(name.substring(1));
      } else {
        namesBuilder.add(name);
      }
    }
    return new NamespacedAttribute(
        typeProvider,
        celValueConverter,
        namesBuilder.build(),
        disambiguateNames,
        ImmutableList.of());
  }

  NamespacedAttribute(
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter,
      ImmutableSet<String> namespacedNames,
      boolean disambiguateNames,
      ImmutableList<Qualifier> qualifiers) {
    this.typeProvider = typeProvider;
    this.celValueConverter = celValueConverter;
    this.namespacedNames = namespacedNames;
    this.disambiguateNames = disambiguateNames;
    this.qualifiers = qualifiers;
  }
}
