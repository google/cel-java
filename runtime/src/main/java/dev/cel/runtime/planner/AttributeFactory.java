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
import dev.cel.common.CelContainer;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.values.CelValueConverter;

@Immutable
final class AttributeFactory {

  private final CelContainer container;
  private final CelTypeProvider typeProvider;
  private final CelValueConverter celValueConverter;

  NamespacedAttribute newAbsoluteAttribute(String... names) {
    return new NamespacedAttribute(typeProvider, celValueConverter, ImmutableSet.copyOf(names));
  }

  RelativeAttribute newRelativeAttribute(PlannedInterpretable operand) {
    return new RelativeAttribute(operand, celValueConverter);
  }

  MaybeAttribute newMaybeAttribute(String name) {
    return new MaybeAttribute(
        this,
        ImmutableList.of(
            new NamespacedAttribute(
                typeProvider, celValueConverter, container.resolveCandidateNames(name))));
  }

  static AttributeFactory newAttributeFactory(
      CelContainer celContainer,
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter) {
    return new AttributeFactory(celContainer, typeProvider, celValueConverter);
  }

  private AttributeFactory(
      CelContainer container, CelTypeProvider typeProvider, CelValueConverter celValueConverter) {
    this.container = container;
    this.typeProvider = typeProvider;
    this.celValueConverter = celValueConverter;
  }
}
