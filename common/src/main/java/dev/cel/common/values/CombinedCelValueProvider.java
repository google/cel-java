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

package dev.cel.common.values;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link CombinedCelValueProvider} takes one or more {@link CelValueProvider} instances and
 * attempts to create a {@link CelValue} instance for a given struct type name by calling each value
 * provider in the order that they are provided to the constructor.
 */
@Immutable
public final class CombinedCelValueProvider implements CelValueProvider {
  private final ImmutableList<CelValueProvider> celValueProviders;

  /** Combines the provided first and second {@link CelValueProvider}. */
  public static CombinedCelValueProvider combine(CelValueProvider... providers) {
    checkArgument(providers.length >= 2, "You must provide two or more providers");
    return new CombinedCelValueProvider(ImmutableList.copyOf(providers));
  }

  @Override
  public Optional<CelValue> newValue(String structType, Map<String, Object> fields) {
    for (CelValueProvider provider : celValueProviders) {
      Optional<CelValue> newValue = provider.newValue(structType, fields);
      if (newValue.isPresent()) {
        return newValue;
      }
    }

    return Optional.empty();
  }

  /** Returns the underlying {@link CelValueProvider}s in order. */
  public ImmutableList<CelValueProvider> valueProviders() {
    return celValueProviders;
  }

  private CombinedCelValueProvider(ImmutableList<CelValueProvider> providers) {
    celValueProviders = checkNotNull(providers);
  }
}
