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

import dev.cel.common.CelOptions;

/** Helper class to construct new {@code CelRuntime} instances. */
public final class CelRuntimeFactory {

  /**
   * Create a new builder for constructing a {@code CelRuntime} instance.
   *
   * <p>Note, the {@link CelOptions#current}, standard CEL function libraries, and linked message
   * evaluation are enabled by default.
   */
  public static CelRuntimeBuilder standardCelRuntimeBuilder() {
    return CelRuntimeLegacyImpl.newBuilder()
        .setOptions(CelOptions.current().build()) // Test
        .setStandardEnvironmentEnabled(false);
  }

  private CelRuntimeFactory() {}
}
