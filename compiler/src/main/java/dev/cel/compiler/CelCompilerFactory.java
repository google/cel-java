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

package dev.cel.compiler;

import dev.cel.checker.CelCheckerBuilder;
import dev.cel.checker.CelCheckerLegacyImpl;
import dev.cel.common.CelOptions;
import dev.cel.parser.CelParserImpl;

/** Factory class for creating builders for type-checker and compiler instances. */
public final class CelCompilerFactory {

  /**
   * Create a new builder to construct a {@code CelChecker} instance.
   *
   * <p>Note, the {@link CelOptions#current} and the standard CEL function declarations are
   * configured by default.
   */
  public static CelCheckerBuilder standardCelCheckerBuilder() {
    return CelCheckerLegacyImpl.newBuilder()
        .setOptions(CelOptions.current().build())
        .setStandardEnvironmentEnabled(true);
  }

  /**
   * Create a new builder to construct a {@link CelCompiler} instance.
   *
   * <p>Note, the {@link CelOptions#current} and the standard CEL function declarations are
   * configured by default.
   */
  public static CelCompilerBuilder standardCelCompilerBuilder() {

    return CelCompilerImpl.newBuilder(CelParserImpl.newBuilder(), CelCheckerLegacyImpl.newBuilder())
        .setOptions(CelOptions.current().build())
        .setStandardEnvironmentEnabled(true);
  }

  private CelCompilerFactory() {}
}
