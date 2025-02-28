// Copyright 2024 Google LLC
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

import com.google.errorprone.annotations.Immutable;

/** Interface describing the general signature of all CEL custom function implementations. */
@SuppressWarnings("AndroidJdkLibsChecker") // FunctionalInterface added in 24
@Immutable
@FunctionalInterface
public interface CelFunctionOverload extends FunctionOverload {

  /**
   * Helper interface for describing unary functions where the type-parameter is used to improve
   * compile-time correctness of function bindings.
   */
  @Immutable
  @FunctionalInterface
  interface Unary<T> extends FunctionOverload.Unary<T> {}

  /**
   * Helper interface for describing binary functions where the type parameters are used to improve
   * compile-time correctness of function bindings.
   */
  @Immutable
  @FunctionalInterface
  interface Binary<T1, T2> extends FunctionOverload.Binary<T1, T2> {}
}
