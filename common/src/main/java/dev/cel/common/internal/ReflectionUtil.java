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

import com.google.common.reflect.TypeToken;
import dev.cel.common.annotations.Internal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  /**
   * Extracts the element type of a container type (List, Map, or Optional). Returns the type itself
   * if it's not a container or if generic type info is missing.
   */
  public static Class<?> getElementType(Class<?> type, Type genericType) {
    TypeToken<?> token = TypeToken.of(genericType);

    if (List.class.isAssignableFrom(type)) {
      return token.resolveType(List.class.getTypeParameters()[0]).getRawType();
    }
    if (Map.class.isAssignableFrom(type)) {
      return token.resolveType(Map.class.getTypeParameters()[1]).getRawType();
    }
    if (type == Optional.class) {
      return token.resolveType(Optional.class.getTypeParameters()[0]).getRawType();
    }

    return type;
  }

  /**
   * Extracts the raw Class from a Type. Handles Class, ParameterizedType, and WildcardType (returns
   * upper bound). Returns Object.class as fallback.
   */
  public static Class<?> getRawType(Type type) {
    return TypeToken.of(type).getRawType();
  }

  /**
   * Extracts the actual type arguments from a ParameterizedType, if it has at least the expected
   * minimum number of arguments. Returns Optional.empty() if the type is not parameterized or has
   * fewer arguments than expected.
   */
  public static Optional<Type[]> getTypeArguments(Type type, int minArgs) {
    if (type instanceof ParameterizedType) {
      Type[] args = ((ParameterizedType) type).getActualTypeArguments();
      if (args.length >= minArgs) {
        return Optional.of(args);
      }
    }
    return Optional.empty();
  }

  private ReflectionUtil() {}
}
