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
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.TypeType;
import dev.cel.runtime.GlobalResolver;

@Immutable
interface Attribute {
  Object resolve(GlobalResolver ctx);

  final class MaybeAttribute implements Attribute {
    private final ImmutableList<Attribute> attributes;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (Attribute attr : attributes) {
        Object value = attr.resolve(ctx);
        if (value != null) {
          return value;
        }
      }

      // TODO: Handle unknowns
      throw new UnsupportedOperationException("Unknown attributes is not supported yet");
    }

    MaybeAttribute(ImmutableList<Attribute> attributes) {
      this.attributes = attributes;
    }
  }

  final class NamespacedAttribute implements Attribute {
    private final ImmutableList<String> namespacedNames;
    private final CelTypeProvider typeProvider;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (String name : namespacedNames) {
        Object value = ctx.resolve(name);
        if (value != null) {
          // TODO: apply qualifiers
          return value;
        }

        TypeType type = typeProvider.findType(name).map(TypeType::create).orElse(null);
        if (type != null) {
          return type;
        }
      }

      // TODO: Handle unknowns
      throw new UnsupportedOperationException("Unknown attributes is not supported yet");
    }

    NamespacedAttribute(CelTypeProvider typeProvider, ImmutableList<String> namespacedNames) {
      this.typeProvider = typeProvider;
      this.namespacedNames = namespacedNames;
    }
  }
}
