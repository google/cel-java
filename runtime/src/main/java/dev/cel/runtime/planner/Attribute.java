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
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.EnumType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.SelectableValue;
import dev.cel.runtime.GlobalResolver;
import java.util.NoSuchElementException;

@Immutable
interface Attribute {
  Object resolve(GlobalResolver ctx);

  Attribute addQualifier(Qualifier qualifier);

  @Immutable
  final class MaybeAttribute implements Attribute {
    private final AttributeFactory attrFactory;
    private final ImmutableList<NamespacedAttribute> attributes;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (NamespacedAttribute attr : attributes) {
        Object value = attr.resolve(ctx);
        if (value != null) {
          return value;
        }
      }

      // TODO: Handle unknowns
      throw new UnsupportedOperationException("Unknown attributes is not supported yet");
    }

    @Override
    public Attribute addQualifier(Qualifier qualifier) {
      Object strQualifier = qualifier.value();
      ImmutableList.Builder<String> augmentedNamesBuilder = ImmutableList.builder();
      ImmutableList.Builder<NamespacedAttribute> attributesBuilder = ImmutableList.builder();
      for (NamespacedAttribute attr : attributes) {
        if (strQualifier instanceof String && attr.qualifiers.isEmpty()) {
          for (String varName : attr.candidateVariableNames()) {
            augmentedNamesBuilder.add(varName + "." + strQualifier);
          }
        }

        attributesBuilder.add(attr.addQualifier(qualifier));
      }
      ImmutableList<String> augmentedNames = augmentedNamesBuilder.build();
      ImmutableList.Builder<NamespacedAttribute> namespacedAttributeBuilder =
          ImmutableList.builder();
      if (!augmentedNames.isEmpty()) {
        namespacedAttributeBuilder.add(
            attrFactory.newAbsoluteAttribute(augmentedNames.toArray(new String[0])));
      }

      namespacedAttributeBuilder.addAll(attributesBuilder.build());
      return new MaybeAttribute(attrFactory, namespacedAttributeBuilder.build());
    }

    MaybeAttribute(AttributeFactory attrFactory, ImmutableList<NamespacedAttribute> attributes) {
      this.attrFactory = attrFactory;
      this.attributes = attributes;
    }
  }

  @Immutable
  final class NamespacedAttribute implements Attribute {
    private final ImmutableList<String> namespacedNames;
    private final ImmutableList<Qualifier> qualifiers;
    private final CelValueConverter celValueConverter;
    private final CelTypeProvider typeProvider;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (String name : namespacedNames) {
        Object value = ctx.resolve(name);
        if (value != null) {
          if (!qualifiers.isEmpty()) {
            return applyQualifiers(value, qualifiers);
          } else {
            return value;
          }
        }

        CelType type = typeProvider.findType(name).orElse(null);
        if (type != null) {
          if (qualifiers.isEmpty()) {
            // Resolution of a fully qualified type name: foo.bar.baz
            return TypeType.create(type);
          } else {
            // This is potentially a fully qualified reference to an enum value
            if (type instanceof EnumType && qualifiers.size() == 1) {
              EnumType enumType = (EnumType) type;
              String strQualifier = (String) qualifiers.get(0).value();
              return enumType
                  .findNumberByName(strQualifier)
                  .orElseThrow(
                      () ->
                          new NoSuchElementException(
                              String.format(
                                  "Field %s was not found on enum %s", enumType, strQualifier)));
            }
          }
          return type;
        }
      }

      return null;
    }

    private Object applyQualifiers(Object value, ImmutableList<Qualifier> qualifiers) {
      Object obj = celValueConverter.toRuntimeValue(value);

      for (Qualifier qualifier : qualifiers) {
        obj = ((SelectableValue<Object>) obj).select(qualifier.value());
      }

      // TODO
      if (obj instanceof CelValue) {
        obj = celValueConverter.unwrap((CelValue) obj);
      }

      return obj;
    }

    private ImmutableList<String> candidateVariableNames() {
      return namespacedNames;
    }

    private ImmutableList<Qualifier> qualifiers() {
      return qualifiers;
    }

    @Override
    public NamespacedAttribute addQualifier(Qualifier qualifier) {
      return new NamespacedAttribute(
          typeProvider,
          celValueConverter,
          namespacedNames,
          ImmutableList.<Qualifier>builder().addAll(qualifiers).add(qualifier).build());
    }

    NamespacedAttribute(
        CelTypeProvider typeProvider,
        CelValueConverter celValueConverter,
        ImmutableList<String> namespacedNames) {
      this(typeProvider, celValueConverter, namespacedNames, ImmutableList.of());
    }

    private NamespacedAttribute(
        CelTypeProvider typeProvider,
        CelValueConverter celValueConverter,
        ImmutableList<String> namespacedNames,
        ImmutableList<Qualifier> qualifiers) {
      this.typeProvider = typeProvider;
      this.celValueConverter = celValueConverter;
      this.namespacedNames = namespacedNames;
      this.qualifiers = qualifiers;
    }
  }
}
