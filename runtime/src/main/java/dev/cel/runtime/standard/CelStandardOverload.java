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

package dev.cel.runtime.standard;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;

/**
 * {@code CelStandardOverload} defines an interface for a standard function's overload. The
 * implementation should produce a concrete {@link CelFunctionBinding} for the standard function's
 * overload.
 */
@Immutable
interface CelStandardOverload {

  CelFunctionBinding newFunctionBinding(CelOptions celOptions, RuntimeEquality runtimeEquality);

  @SuppressWarnings("AndroidJdkLibsChecker") // FunctionalInterface added in 24
  @FunctionalInterface
  @Immutable
  interface FunctionBindingCreator {
    CelFunctionBinding create(CelOptions celOptions, RuntimeEquality runtimeEquality);
  }
}
