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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.CelOptions;

/** Interface for building an instance of CelParser */
public interface CelParserBuilder {
  /** Configures the {@code CelOptions} used to enable fixes within the parser. */
  @CanIgnoreReturnValue
  CelParserBuilder setOptions(CelOptions celOptions);

  /**
   * Sets the macro set defined as part of CEL standard library for the parser, replacing the macros
   * from any prior call.
   */
  @CanIgnoreReturnValue
  CelParserBuilder setStandardMacros(CelStandardMacro... macros);

  /**
   * Sets the macro set defined as part of CEL standard library for the parser, replacing the macros
   * from any prior call.
   */
  @CanIgnoreReturnValue
  CelParserBuilder setStandardMacros(Iterable<CelStandardMacro> macros);

  /**
   * Registers the given macros, replacing any previous macros with the same key.
   *
   * <p>Use this to register a set of user-defined custom macro implementation for the parser. For
   * registering macros defined as part of CEL standard library, use {@link #setStandardMacros}
   * instead.
   *
   * <p>Custom macros should not use the same function names as the ones found in {@link
   * CelStandardMacro} (ex: has, all, exists, etc.). Build method will throw if both standard macros
   * and custom macros are set with the same name.
   */
  @CanIgnoreReturnValue
  CelParserBuilder addMacros(CelMacro... macros);

  /**
   * Registers the given macros, replacing any previous macros with the same key.
   *
   * <p>Use this to register a set of user-defined custom macro implementation for the parser. For
   * registering macros defined as part of CEL standard library, use {@link #setStandardMacros}
   * instead.
   *
   * <p>Custom macros should not use the same function names as the ones found in {@link
   * CelStandardMacro} (ex: has, all, exists, etc.). Build method will throw if both standard macros
   * and custom macros are set with the same name.
   */
  @CanIgnoreReturnValue
  CelParserBuilder addMacros(Iterable<CelMacro> macros);

  /** Adds one or more libraries for parsing */
  @CanIgnoreReturnValue
  CelParserBuilder addLibraries(CelParserLibrary... libraries);

  /** Adds a collection of libraries for parsing */
  @CanIgnoreReturnValue
  CelParserBuilder addLibraries(Iterable<? extends CelParserLibrary> libraries);

  /** Build a new instance of the {@code CelParser}. */
  @CheckReturnValue
  CelParser build();
}

