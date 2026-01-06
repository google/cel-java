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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;

/** Interface describing the general signature of all CEL custom function implementations. */
@Immutable
@FunctionalInterface
public interface CelFunctionOverload {

  /** Evaluate a set of arguments throwing a {@code CelException} on error. */
  Object apply(Object[] args) throws CelEvaluationException;

  /**
   * Helper interface for describing unary functions where the type-parameter is used to improve
   * compile-time correctness of function bindings.
   */
  @Immutable
  @FunctionalInterface
  interface Unary<T> {
    Object apply(T arg) throws CelEvaluationException;
  }

  /**
   * Helper interface for describing binary functions where the type parameters are used to improve
   * compile-time correctness of function bindings.
   */
  @Immutable
  @FunctionalInterface
  interface Binary<T1, T2> {
    Object apply(T1 arg1, T2 arg2) throws CelEvaluationException;
  }

  /**
   * Returns true if the overload's expected argument types match the types of the given arguments.
   */
  static boolean canHandle(
      Object[] arguments, ImmutableList<Class<?>> parameterTypes, boolean isStrict) {
    if (parameterTypes.size() != arguments.length) {
      return false;
    }
    for (int i = 0; i < parameterTypes.size(); i++) {
      Class<?> paramType = parameterTypes.get(i);
      Object arg = arguments[i];
      if (arg == null) {
        // null can be assigned to messages, maps, and to objects.
        // TODO: Remove null special casing
        if (paramType != Object.class && !Map.class.isAssignableFrom(paramType)) {
          return false;
        }
        continue;
      }

      if (arg instanceof Exception || arg instanceof CelUnknownSet) {
        // Only non-strict functions can accept errors/unknowns as arguments to a function
        if (!isStrict) {
          // Skip assignability check below, but continue to validate remaining args
          continue;
        }
      }

      if (!paramType.isAssignableFrom(arg.getClass())) {
        return false;
      }
    }
    return true;
  }
}
