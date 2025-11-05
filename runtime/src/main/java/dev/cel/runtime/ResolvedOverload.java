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
import com.google.protobuf.MessageLite;
import java.util.List;
import java.util.Map;

/**
 * Representation of a function overload which has been resolved to a specific set of argument types
 * and a function definition.
 */
@Immutable
interface ResolvedOverload {

  /** The overload id of the function. */
  String getOverloadId();

  /** The types of the function parameters. */
  List<Class<?>> getParameterTypes();

  /** The function definition. */
  CelFunctionOverload getDefinition();

  /**
   * Denotes whether an overload is strict.
   *
   * <p>A strict function will not be invoked if any of its arguments are an error or unknown value.
   * The runtime automatically propagates the error or unknown instead.
   *
   * <p>A non-strict function will be invoked even if its arguments contain errors or unknowns. The
   * function's implementation is then responsible for handling these values. This is primarily used
   * for short-circuiting logical operators (e.g., `||`, `&&`) and comprehension's
   * internal @not_strictly_false function.
   *
   * <p>In a vast majority of cases, a function should be kept strict.
   */
  boolean isStrict();

  /**
   * Returns true if the overload's expected argument types match the types of the given arguments.
   */
  default boolean canHandle(Object[] arguments) {
    List<Class<?>> parameterTypes = getParameterTypes();
    if (parameterTypes.size() != arguments.length) {
      return false;
    }
    for (int i = 0; i < parameterTypes.size(); i++) {
      Class<?> paramType = parameterTypes.get(i);
      Object arg = arguments[i];
      if (arg == null) {
        // null can be assigned to messages, maps, and to objects.
        if (paramType != Object.class
            && !MessageLite.class.isAssignableFrom(paramType)
            && !Map.class.isAssignableFrom(paramType)) {
          return false;
        }
        continue;
      }

      if (arg instanceof Exception || arg instanceof CelUnknownSet) {
        // Only non-strict functions can accept errors/unknowns as arguments to a function
        if (!isStrict()) {
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
