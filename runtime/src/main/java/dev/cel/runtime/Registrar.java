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

import com.google.errorprone.annotations.Immutable;
import java.util.List;

/**
 * An object which registers the functions that a {@link Dispatcher} calls.
 *
 * @deprecated Do not use. This interface exists solely for legacy async stack compatibility
 *     reasons.
 */
@Deprecated
public interface Registrar {

  /** Interface describing the general signature of all CEL custom function implementations. */
  @Immutable
  interface Function extends CelFunctionOverload {}

  /**
   * Helper interface for describing unary functions where the type-parameter is used to improve
   * compile-time correctness of function bindings.
   */
  @Immutable
  interface UnaryFunction<T> extends CelFunctionOverload.Unary<T> {}

  /**
   * Helper interface for describing binary functions where the type parameters are used to improve
   * compile-time correctness of function bindings.
   */
  @Immutable
  interface BinaryFunction<T1, T2> extends CelFunctionOverload.Binary<T1, T2> {}

  /** Adds a unary function to the dispatcher. */
  <T> void add(String overloadId, Class<T> argType, UnaryFunction<T> function);

  /** Adds a binary function to the dispatcher. */
  <T1, T2> void add(
      String overloadId, Class<T1> argType1, Class<T2> argType2, BinaryFunction<T1, T2> function);

  /** Adds a general function to the dispatcher. */
  void add(String overloadId, List<Class<?>> argTypes, Function function);
}
