// Copyright 2023 Google LLC
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

package dev.cel.bundle;

import dev.cel.checker.CelCheckerLegacyImpl;
import dev.cel.common.CelOptions;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerImpl;
import dev.cel.parser.CelParserImpl;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeLegacyImpl;

/** Helper class to configure the entire CEL stack in a common interface. */
public final class CelFactory {

  private CelFactory() {}

  /**
   * Creates a builder for configuring CEL using current parser for the parse, type-check, and eval
   * of expressions.
   *
   * <p>Note, the {@link CelOptions#current}, standard CEL function libraries, and linked message
   * evaluation are enabled by default.
   */
  public static CelBuilder standardCelBuilder() {
    return CelImpl.newBuilder(
            CelCompilerImpl.newBuilder(
                CelParserImpl.newBuilder(), CelCheckerLegacyImpl.newBuilder()),
            CelRuntimeLegacyImpl.newBuilder())
        .setOptions(CelOptions.current().build())
        .setStandardEnvironmentEnabled(true);
  }

  /** Combines a prebuilt {@link CelCompiler} and {@link CelRuntime} into {@link Cel}. */
  public static Cel combine(CelCompiler celCompiler, CelRuntime celRuntime) {
    return CelImpl.combine(celCompiler, celRuntime);
  }
}
