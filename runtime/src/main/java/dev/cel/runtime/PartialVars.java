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

package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.Optional;

/**
 * A holder for a {@link CelVariableResolver} and a set of {@link CelAttributePattern}s that
 * indicate variables or parts of variables whose value are not yet known.
 */
@AutoValue
public abstract class PartialVars {

  /** The resolver to use for resolving evaluation variables. */
  public abstract CelVariableResolver resolver();

  /**
   * A list of attribute patterns specifying which missing attribute paths should be tracked as
   * unknown values.
   */
  public abstract ImmutableList<CelAttributePattern> unknowns();

  /** Constructs a new {@code PartialVars} from one or more {@link CelAttributePattern}s. */
  public static PartialVars of(CelAttributePattern... unknowns) {
    return of((unused) -> Optional.empty(), ImmutableList.copyOf(unknowns));
  }

  /**
   * Constructs a new {@code PartialVars} from a {@link CelVariableResolver} and a list of {@link
   * CelAttributePattern}s.
   */
  public static PartialVars of(
      CelVariableResolver resolver, Iterable<CelAttributePattern> unknowns) {
    return new AutoValue_PartialVars(resolver, ImmutableList.copyOf(unknowns));
  }

  /**
   * Constructs a new {@code PartialVars} from a map of variables and an array of {@link
   * CelAttributePattern}s.
   */
  public static PartialVars of(Map<String, ?> variables, CelAttributePattern... unknowns) {
    return of(
        (name) -> variables.containsKey(name) ? Optional.of(variables.get(name)) : Optional.empty(),
        unknowns);
  }

  /**
   * Constructs a new {@code PartialVars} from a {@link CelVariableResolver} and an array of {@link
   * CelAttributePattern}s.
   */
  public static PartialVars of(CelVariableResolver resolver, CelAttributePattern... unknowns) {
    return of(resolver, ImmutableList.copyOf(unknowns));
  }
}
