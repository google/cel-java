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

import java.util.Optional;

/**
 * Resolver for attribute lookups at runtime.
 *
 * <p>Attributes identify resolvable parts of state available in the CEL expression.
 */
public interface CelAttributeResolver {
  /**
   * Attempts to resolve an attribute exactly.
   *
   * <p>May return an UnknownSet if the attribute is unknown, a value if it has been resolved, or
   * Optional.empty() if the attribute isn't declared as an unknown.
   */
  Optional<Object> resolve(CelAttribute attr);

  /**
   * Returns an unknown set containing the attribute if it is determined as partially unknown.
   *
   * <p>A partial match is defined as an attribute whose sub-elements match an unknown pattern.
   * E.g., attribute <code>com.google.container</code> is partially unknown with pattern <code>
   * com.google.container.element</code>
   */
  Optional<CelUnknownSet> maybePartialUnknown(CelAttribute attr);
}
