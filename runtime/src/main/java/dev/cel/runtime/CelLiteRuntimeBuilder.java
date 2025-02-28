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

package dev.cel.runtime;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.CelOptions;

/** Interface for building an instance of {@link CelLiteRuntime} */
public interface CelLiteRuntimeBuilder {

  /** Set the {@code CelOptions} used to enable fixes and features for this CEL instance. */
  @CanIgnoreReturnValue
  CelLiteRuntimeBuilder setOptions(CelOptions options);

  /**
   * Override the standard functions for the runtime. This can be used to subset the standard
   * environment to only expose the desired function overloads to the runtime.
   */
  @CanIgnoreReturnValue
  CelLiteRuntimeBuilder setStandardFunctions(CelStandardFunctions standardFunctions);

  /** Add one or more {@link CelFunctionBinding} objects to the CEL runtime. */
  @CanIgnoreReturnValue
  CelLiteRuntimeBuilder addFunctionBindings(CelFunctionBinding... bindings);

  /** Bind a collection of {@link CelFunctionBinding} objects to the runtime. */
  @CanIgnoreReturnValue
  CelLiteRuntimeBuilder addFunctionBindings(Iterable<CelFunctionBinding> bindings);

  @CheckReturnValue
  CelLiteRuntime build();
}
