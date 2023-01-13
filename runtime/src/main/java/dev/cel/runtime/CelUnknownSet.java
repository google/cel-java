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

package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

/**
 * Unknown set representation.
 *
 * <p>An unknown set is the collection of unknowns that were encountered while evaluating a CEL
 * expression and not pruned via logical operators.
 *
 * <p>Note: dependant unknown values may not be included, so it's possible that re-evaluating the
 * same expression with all the unknowns resolved will result in a different unknown set.
 */
@AutoValue
public abstract class CelUnknownSet {
  public abstract ImmutableSet<CelAttribute> attributes();

  public static CelUnknownSet create(ImmutableSet<CelAttribute> attributes) {
    return new AutoValue_CelUnknownSet(attributes);
  }

  public static CelUnknownSet create(CelAttribute attribute) {
    return create(ImmutableSet.of(attribute));
  }

  public static CelUnknownSet union(CelUnknownSet lhs, CelUnknownSet rhs) {
    return create(
        ImmutableSet.<CelAttribute>builder()
            .addAll(lhs.attributes())
            .addAll(rhs.attributes())
            .build());
  }

  public CelUnknownSet merge(CelUnknownSet rhs) {
    return union(this, rhs);
  }
}
