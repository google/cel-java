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

package dev.cel.extensions;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Set;

/**
 * Collections of supported CEL Extensions for the lite runtime.
 *
 * <p>To use, supply the desired extensions using : {@code CelLiteRuntimeBuilder#addLibraries}.
 */
public final class CelLiteExtensions {

  /**
   * Extended functions for Set manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include all functions denoted in {@link SetsFunction}, including any future
   * additions. To expose only a subset of functions, use {@link #sets(CelOptions, SetsFunction...)}
   * instead.
   */
  public static SetsExtensionsRuntimeImpl sets(CelOptions celOptions) {
    return sets(celOptions, SetsFunction.values());
  }

  /**
   * Extended functions for Set manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include only the specific functions denoted by {@link SetsFunction}.
   */
  public static SetsExtensionsRuntimeImpl sets(CelOptions celOptions, SetsFunction... functions) {
    return sets(celOptions, ImmutableSet.copyOf(functions));
  }

  /**
   * Extended functions for Set manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include only the specific functions denoted by {@link SetsFunction}.
   */
  public static SetsExtensionsRuntimeImpl sets(CelOptions celOptions, Set<SetsFunction> functions) {
    RuntimeEquality runtimeEquality = RuntimeEquality.create(RuntimeHelpers.create(), celOptions);
    return new SetsExtensionsRuntimeImpl(runtimeEquality, functions);
  }

  private CelLiteExtensions() {}
}
