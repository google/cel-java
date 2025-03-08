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

package dev.cel.common.internal;

import dev.cel.common.annotations.Internal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class for invoking Java reflection.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class ReflectionUtil {

  public static Method getMethod(Class<?> clazz, String methodName, Class<?>... params) {
    try {
      return clazz.getMethod(methodName, params);
    } catch (NoSuchMethodException e) {
      throw new LinkageError(
          String.format("method [%s] does not exist in class: [%s].", methodName, clazz.getName()),
          e);
    }
  }

  public static Object invoke(Method method, Object object, Object... params) {
    try {
      return method.invoke(object, params);
    } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
      throw new LinkageError(
          String.format(
              "method [%s] invocation failed on class [%s].",
              method.getName(), method.getDeclaringClass()),
          e);
    }
  }

  private ReflectionUtil() {}
}
