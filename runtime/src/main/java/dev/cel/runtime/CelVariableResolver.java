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

/** Functional interface that exposes a method to find a CEL variable value by name. */
@FunctionalInterface
public interface CelVariableResolver {

  /**
   * Find a variable value, if present, by {@code name}.
   *
   * <p>Variable {@code String} names may be simple, e.g. {@code status} or qualified {@code
   * pkg.qual.status}. CEL resolves identifiers within an expression {@code container} using
   * protobuf namespace resolution rules.
   *
   * <p>For example, given an expression container name {@code a.b.c.M.N} and a variable name {@code
   * R.s}, CEL evaluators will try resolve names in the following order:
   *
   * <ol>
   *   <li>{@code a.b.c.M.N.R.s}
   *   <li>{@code a.b.c.M.R.s}
   *   <li>{@code a.b.c.R.s}
   *   <li>{@code a.b.R.s}
   *   <li>{@code a.R.s}
   *   <li>{@code R.s}
   * </ol>
   */
  Optional<Object> find(String name);

  /**
   * Chain two variable resolvers together, using the {@code primary} as the initial variable source
   * to consider, and the {@code secondary} as a backup.
   *
   * <p>When a variable appears in both resolvers, then the {@code primary} variable value will
   * shadow the value in the {@code secondary}.
   */
  static CelVariableResolver hierarchicalVariableResolver(
      CelVariableResolver primary, CelVariableResolver secondary) {
    return (name) -> {
      Optional<Object> value = primary.find(name);
      return value.isPresent() ? value : secondary.find(name);
    };
  }
}
