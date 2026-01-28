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

import com.google.common.collect.ImmutableCollection;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.TypeType;
import java.util.Optional;

/**
 * The {@code TypeProviderLegacyImpl} acts as a bridge between the old and new type provider APIs
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@CheckReturnValue
@Internal
final class TypeProviderLegacyImpl implements CelTypeProvider {

  // Legacy typeProvider is immutable, just not marked as such
  @SuppressWarnings("Immutable")
  private final TypeProvider typeProvider;

  TypeProviderLegacyImpl(TypeProvider typeProvider) {
    this.typeProvider = typeProvider;
  }

  @Override
  public ImmutableCollection<CelType> types() {
    return typeProvider.types();
  }

  @Override
  public Optional<CelType> findType(String typeName) {
    TypeType type = typeProvider
        .lookupCelType(typeName)
        .orElse(null);

    if (type == null) {
      return Optional.empty();
    }

    return Optional.of(type.type());
  }
}
