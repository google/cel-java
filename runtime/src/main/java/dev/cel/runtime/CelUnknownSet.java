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

  /**
   * Set of attributes with a series of selection or index operations marked unknown. This set is
   * always empty if enableUnknownTracking is disabled in {@code CelOptions}.
   */
  public abstract ImmutableSet<CelAttribute> attributes();

  /** Set of subexpression IDs that were decided to be unknown and in the critical path. */
  public abstract ImmutableSet<Long> unknownExprIds();

  public static CelUnknownSet create(CelAttribute attribute) {
    return create(ImmutableSet.of(attribute));
  }

  public static CelUnknownSet create(ImmutableSet<CelAttribute> attributes) {
    return create(attributes, ImmutableSet.of());
  }

  public static CelUnknownSet create(Long... unknownExprIds) {
    return create(ImmutableSet.copyOf(unknownExprIds));
  }

  public static CelUnknownSet create(CelAttribute attribute, Iterable<Long> unknownExprIds) {
    return create(ImmutableSet.of(attribute), ImmutableSet.copyOf(unknownExprIds));
  }

  static CelUnknownSet create(Iterable<Long> unknownExprIds) {
    return create(ImmutableSet.of(), ImmutableSet.copyOf(unknownExprIds));
  }

  private static CelUnknownSet create(
      ImmutableSet<CelAttribute> attributes, ImmutableSet<Long> unknownExprIds) {
    return new AutoValue_CelUnknownSet(attributes, unknownExprIds);
  }

  public static CelUnknownSet union(CelUnknownSet lhs, CelUnknownSet rhs) {
    return create(
        ImmutableSet.<CelAttribute>builder()
            .addAll(lhs.attributes())
            .addAll(rhs.attributes())
            .build(),
        ImmutableSet.<Long>builder()
            .addAll(lhs.unknownExprIds())
            .addAll(rhs.unknownExprIds())
            .build());
  }

  public CelUnknownSet merge(CelUnknownSet rhs) {
    return union(this, rhs);
  }
}
