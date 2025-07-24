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

package dev.cel.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.LinkedHashMap;
import java.util.Optional;

/** CelContainer holds a reference to an optional qualified container name and set of aliases. */
@AutoValue
@Immutable
public abstract class CelContainer {

  public abstract String name();

  abstract ImmutableMap<String, String> aliases();

  /** Builder for {@link CelContainer} */
  @AutoValue.Builder
  public abstract static class Builder {

    private final LinkedHashMap<String, String> aliases = new LinkedHashMap<>();

    /** Sets the fully-qualified name of the container. */
    public abstract Builder setName(String name);

    abstract Builder setAliases(ImmutableMap<String, String> aliases);

    /** Alias associates a fully-qualified name with a user-defined alias. */
    @CanIgnoreReturnValue
    public Builder addAlias(String alias, String qualifiedName) {
      validateAliasOrThrow("alias", qualifiedName, alias);
      aliases.put(alias, qualifiedName);
      return this;
    }

    private void validateAliasOrThrow(String kind, String qualifiedName, String alias) {
      if (alias.isEmpty() || alias.contains(".")) {
        throw new IllegalArgumentException(
            String.format(
                "%s must be non-empty and simple (not qualified): %s=%s", kind, kind, alias));
      }

      if (qualifiedName.charAt(0) == '.') {
        throw new IllegalArgumentException(
            String.format("qualified name must not begin with a leading '.': %s", qualifiedName));
      }

      int index = qualifiedName.lastIndexOf(".");
      if (index <= 0 || index == qualifiedName.length() - 1) {
        throw new IllegalArgumentException(
            String.format("%s must refer to a valid qualified name: %s", kind, qualifiedName));
      }

      String aliasRef = aliases.get(alias);
      if (aliasRef != null) {
        throw new IllegalArgumentException(
            String.format(
                "%s collides with existing reference: name=%s, %s=%s, existing=%s",
                kind, qualifiedName, kind, alias, aliasRef));
      }
    }

    abstract CelContainer autoBuild();

    @CheckReturnValue
    public CelContainer build() {
      setAliases(ImmutableMap.copyOf(aliases));
      return autoBuild();
    }
  }

  /**
   * Returns the candidates name of namespaced identifiers in C++ resolution order.
   *
   * <p>Names which shadow other names are returned first. If a name includes a leading dot ('.'),
   * the name is treated as an absolute identifier which cannot be shadowed.
   *
   * <p>Given a container name a.b.c.M.N and a type name R.s, this will deliver in order:
   *
   * <ul>
   *   <li>a.b.c.M.N.R.s
   *   <li>a.b.c.M.R.s
   *   <li>a.b.c.R.s
   *   <li>a.b.R.s
   *   <li>a.R.s
   *   <li>R.s
   * </ul>
   *
   * <p>If aliases or abbreviations are configured for the container, then alias names will take
   * precedence over containerized names.
   */
  public ImmutableSet<String> resolveCandidateNames(String typeName) {
    if (typeName.startsWith(".")) {
      String qualifiedName = typeName.substring(1);
      String alias = findAlias(qualifiedName).orElse(qualifiedName);

      return ImmutableSet.of(alias);
    }

    String alias = findAlias(typeName).orElse(null);
    if (alias != null) {
      return ImmutableSet.of(alias);
    }

    if (name().isEmpty()) {
      return ImmutableSet.of(typeName);
    }

    String nextContainer = name();
    ImmutableSet.Builder<String> candidates =
        ImmutableSet.<String>builder().add(nextContainer + "." + typeName);
    for (int i = nextContainer.lastIndexOf("."); i >= 0; i = nextContainer.lastIndexOf(".")) {
      nextContainer = nextContainer.substring(0, i);
      candidates.add(nextContainer + "." + typeName);
    }

    return candidates.add(typeName).build();
  }

  private Optional<String> findAlias(String name) {
    // If an alias exists for the name, ensure it is searched last.
    String simple = name;
    String qualifier = "";
    int dot = name.indexOf(".");
    if (dot > 0) {
      simple = name.substring(0, dot);
      qualifier = name.substring(dot);
    }
    String alias = aliases().get(simple);
    if (alias == null) {
      return Optional.empty();
    }

    return Optional.of(alias + qualifier);
  }

  public static Builder newBuilder() {
    return new AutoValue_CelContainer.Builder().setName("");
  }

  public static CelContainer ofName(String containerName) {
    return newBuilder().setName(containerName).build();
  }
}
