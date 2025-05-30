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

/**
 * CelRuntimeLibrary defines the interface to extend functionalities beyond the CEL standard
 * functions for {@link CelRuntime}.
 */
public interface CelRuntimeLibrary {

  /**
   * Configures the runtime to support the library implementation, such as adding function bindings.
   */
  void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder);
}
