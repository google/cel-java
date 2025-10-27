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
import dev.cel.common.CelContainer;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.runtime.planner.Attribute.MaybeAttribute;
import dev.cel.runtime.planner.Attribute.NamespacedAttribute;

@Immutable
final class AttributeFactory {

  private final CelContainer unusedContainer;
  private final CelTypeProvider typeProvider;

  NamespacedAttribute newAbsoluteAttribute(String... names) {
    return new NamespacedAttribute(typeProvider, ImmutableList.copyOf(names));
  }

  MaybeAttribute newMaybeAttribute(String... names) {
    // TODO: Resolve container names
    return new MaybeAttribute(
        ImmutableList.of(new NamespacedAttribute(typeProvider, ImmutableList.copyOf(names))));
  }

  static AttributeFactory newAttributeFactory(
      CelContainer celContainer, CelTypeProvider typeProvider) {
    return new AttributeFactory(celContainer, typeProvider);
  }

  private AttributeFactory(CelContainer container, CelTypeProvider typeProvider) {
    this.unusedContainer = container;
    this.typeProvider = typeProvider;
  }
}
