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

package dev.cel.parser;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationResult;
import dev.cel.common.annotations.Internal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modernized parser implementation for CEL.
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should use factories, such as {@link
 * CelParserFactory} instead to instantiate a parser.
 */
@Immutable
@Internal
public final class CelParserImpl implements CelParser {

  // Common feature flags to be used with all calls.

  // Set of macros configured for parsing.
  private final ImmutableMap<String, CelMacro> macros;

  // Specific options for limits on parsing power.
  private final CelOptions options;

  /** Creates a new {@link Builder}. */
  public static Builder newBuilder() {
    return new Builder().setOptions(CelOptions.DEFAULT);
  }

  @Override
  public CelValidationResult parse(String expression, String description) {
    return parse(CelSource.newBuilder(expression).setDescription(description).build());
  }

  @Override
  public CelValidationResult parse(CelSource source) {
    return Parser.parse(this, source, getOptions());
  }

  Optional<CelMacro> findMacro(String key) {
    return Optional.ofNullable(macros.get(key));
  }

  /** Return the options the {@link CelParser} was originally created with. */
  public CelOptions getOptions() {
    return options;
  }

  /** Builder for {@link CelParserImpl}. */
  public static final class Builder implements CelParserBuilder {

    private final List<CelStandardMacro> standardMacros;
    private final Map<String, CelMacro> macros;
    private final ImmutableSet.Builder<CelParserLibrary> celParserLibraries;
    private CelOptions options;

    @Override
    @CanIgnoreReturnValue
    public Builder setStandardMacros(CelStandardMacro... macros) {
      checkNotNull(macros);
      return setStandardMacros(Arrays.asList(macros));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setStandardMacros(Iterable<CelStandardMacro> macros) {
      checkNotNull(macros);
      this.standardMacros.clear();
      Iterables.addAll(this.standardMacros, macros);
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addMacros(CelMacro... macros) {
      checkNotNull(macros);
      return addMacros(Arrays.asList(macros));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addMacros(Iterable<CelMacro> macros) {
      checkNotNull(macros);
      for (CelMacro m : macros) {
        CelMacro macro = checkNotNull(m);
        this.macros.put(macro.getKey(), macro);
      }
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(CelParserLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(Iterable<? extends CelParserLibrary> libraries) {
      checkNotNull(libraries);
      this.celParserLibraries.addAll(libraries);
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setOptions(CelOptions options) {
      this.options = checkNotNull(options);
      return this;
    }

    @Override
    public CelOptions getOptions() {
      return this.options;
    }

    @Override
    @CheckReturnValue
    public CelParserImpl build() {
      ImmutableSet<CelParserLibrary> parserLibrarySet = celParserLibraries.build();

      // Add libraries, such as extensions
      parserLibrarySet.forEach(celLibrary -> celLibrary.setParserOptions(this));

      ImmutableMap.Builder<String, CelMacro> builder = ImmutableMap.builder();
      builder.putAll(macros);
      standardMacros.stream()
          .map(CelStandardMacro::getDefinition)
          .forEach(celMacro -> builder.put(celMacro.getKey(), celMacro));
      return new CelParserImpl(builder.buildOrThrow(), checkNotNull(options));
    }

    private Builder() {
      this.macros = new HashMap<>();
      this.celParserLibraries = ImmutableSet.builder();
      this.standardMacros = new ArrayList<>();
    }
  }

  private CelParserImpl(ImmutableMap<String, CelMacro> macros, CelOptions options) {
    this.macros = macros;
    this.options = options;
  }
}
