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
      if (!paramType.isAssignableFrom(arg.getClass())) {
        return false;
      }
    }
    return true;
  }
}
