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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.LinkedHashMap;
import java.util.Locale;
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

    abstract String name();

    /** Sets the fully-qualified name of the container. */
    public abstract Builder setName(String name);

    abstract Builder setAliases(ImmutableMap<String, String> aliases);

    /** See {@link #addAbbreviations(ImmutableSet)} for documentation. */
    @CanIgnoreReturnValue
    public Builder addAbbreviations(String... qualifiedNames) {
      Preconditions.checkNotNull(qualifiedNames);
      return addAbbreviations(ImmutableSet.copyOf(qualifiedNames));
    }

    /**
     * Configures a set of simple names as abbreviations for fully-qualified names.
     *
     * <p>An abbreviation is a simple name that expands to a fully-qualified name. Abbreviations can
     * be useful when working with variables, functions, and especially types from multiple
     * namespaces:
     *
     * <pre>{@code
     * // CEL object construction
     * qual.pkg.version.ObjTypeName{
     *   field: alt.container.ver.FieldTypeName{value: ...}
     * }
     * }</pre>
     *
     * <p>Only one the qualified names above may be used as the CEL container, so at least one of
     * these references must be a long qualified name within an otherwise short CEL program. Using
     * the following abbreviations, the program becomes much simpler:
     *
     * <pre>{@code
     * // CEL Java option
     * CelContainer.newBuilder().addAbbreviations("qual.pkg.version.ObjTypeName", "alt.container.ver.FieldTypeName").build()
     * }
     * {@code
     * // Simplified Object construction
     * ObjTypeName{field: FieldTypeName{value: ...}}
     * }</pre>
     *
     * <p>There are a few rules for the qualified names and the simple abbreviations generated from
     * them:
     *
     * <ul>
     *   <li>Qualified names must be dot-delimited, e.g. `package.subpkg.name`.
     *   <li>The last element in the qualified name is the abbreviation.
     *   <li>Abbreviations must not collide with each other.
     *   <li>The abbreviation must not collide with unqualified names in use.
     * </ul>
     *
     * <p>Abbreviations are distinct from container-based references in the following important
     * ways:
     *
     * <ul>
     *   <li>Abbreviations must expand to a fully-qualified name.
     *   <li>Expanded abbreviations do not participate in namespace resolution.
     *   <li>Abbreviation expansion is done instead of the container search for a matching
     *       identifier.
     *   <li>Containers follow C++ namespace resolution rules with searches from the most qualified
     *       name to the least qualified name.
     *   <li>Container references within the CEL program may be relative, and are resolved to fully
     *       qualified names at either type-check time or program plan time, whichever comes first.
     * </ul>
     *
     * <p>If there is ever a case where an identifier could be in both the container and as an
     * abbreviation, the abbreviation wins as this will ensure that the meaning of a program is
     * preserved between compilations even as the container evolves.
     *
     * @throws IllegalArgumentException If qualifiedName is invalid per above specification.
     */
    @CanIgnoreReturnValue
    public Builder addAbbreviations(ImmutableSet<String> qualifiedNames) {
      for (String qualifiedName : qualifiedNames) {
        qualifiedName = qualifiedName.trim();
        for (int i = 0; i < qualifiedName.length(); i++) {
          if (!isIdentifierChar(qualifiedName.charAt(i))) {
            throw new IllegalArgumentException(
                String.format(
                    "invalid qualified name: %s, wanted name of the form 'qualified.name'",
                    qualifiedName));
          }
        }

        int index = qualifiedName.lastIndexOf(".");
        if (index <= 0 || index >= qualifiedName.length() - 1) {
          throw new IllegalArgumentException(
              String.format(
                  "invalid qualified name: %s, wanted name of the form 'qualified.name'",
                  qualifiedName));
        }

        String alias = qualifiedName.substring(index + 1);
        aliasAs(AliasKind.ABBREVIATION, qualifiedName, alias);
      }

      return this;
    }

    /**
     * Alias associates a fully-qualified name with a user-defined alias.
     *
     * <p>In general, {@link #addAbbreviations} is preferred to aliasing since the names generated
     * from the Abbrevs option are more easily traced back to source code. Aliasing is useful for
     * propagating alias configuration from one container instance to another, and may also be
     * useful for remapping poorly chosen protobuf message / package names.
     *
     * <p>Note: all the rules that apply to abbreviations also apply to aliasing.
     *
     * <p>Note: It is also possible to alias a top-level package or a name that does not contain a
     * period. When resolving an identifier, CEL checks for variables and functions before
     * attempting to expand aliases for type resolution. Therefore, if an expression consists solely
     * of an identifier that matches both an alias and a declared variable (e.g., {@code
     * short_alias}), the variable will take precedence and the compilation will succeed. The alias
     * expansion will only be used when the alias is a prefix to a longer name (e.g., {@code
     * short_alias.TestRequest}) or if no variable with the same name exists, in which case using
     * the alias as a standalone identifier will likely result in a compilation error.
     *
     * @param alias Simple name to be expanded. Must be a valid identifier.
     * @param qualifiedName The fully qualified name to expand to. This may be a simple name (e.g. a
     *     package name) but it must be a valid identifier.
     */
    @CanIgnoreReturnValue
    public Builder addAlias(String alias, String qualifiedName) {
      aliasAs(AliasKind.ALIAS, qualifiedName, alias);
      return this;
    }

    private void aliasAs(AliasKind kind, String qualifiedName, String alias) {
      validateAliasOrThrow(kind, qualifiedName, alias);
      aliases.put(alias, qualifiedName);
    }

    private void validateAliasOrThrow(AliasKind kind, String qualifiedName, String alias) {
      if (alias.isEmpty() || alias.contains(".")) {
        throw new IllegalArgumentException(
            String.format(
                "%s must be non-empty and simple (not qualified): %s=%s", kind, kind, alias));
      }

      if (qualifiedName.charAt(0) == '.') {
        throw new IllegalArgumentException(
            String.format("qualified name must not begin with a leading '.': %s", qualifiedName));
      }

      String aliasRef = aliases.get(alias);
      if (aliasRef != null) {
        throw new IllegalArgumentException(
            String.format(
                "%s collides with existing reference: name=%s, %s=%s, existing=%s",
                kind, qualifiedName, kind, alias, aliasRef));
      }

      String containerName = name();
      if (containerName.startsWith(alias + ".") || containerName.equals(alias)) {
        throw new IllegalArgumentException(
            String.format(
                "%s collides with container name: name=%s, %s=%s, container=%s",
                kind, qualifiedName, kind, alias, containerName));
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

  abstract Builder autoToBuilder();

  public Builder toBuilder() {
    Builder builder = autoToBuilder();
    builder.aliases.putAll(aliases());
    return builder;
  }

  public static Builder newBuilder() {
    return new AutoValue_CelContainer.Builder().setName("");
  }

  public static CelContainer ofName(String containerName) {
    return newBuilder().setName(containerName).build();
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

  private static boolean isIdentifierChar(int r) {
    if (r > 127) {
      // Not ASCII
      return false;
    }

    return r == '.' || r == '_' || Character.isLetter(r) || Character.isDigit(r);
  }

  private enum AliasKind {
    ALIAS,
    ABBREVIATION;

    @Override
    public String toString() {
      return this.name().toLowerCase(Locale.getDefault());
    }
  }
}
