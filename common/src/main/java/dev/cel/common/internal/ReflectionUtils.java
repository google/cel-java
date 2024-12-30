package dev.cel.common.internal;

import dev.cel.common.annotations.Internal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Internal
public final class ReflectionUtils {

  public static Method getMethod(String className, String methodName, Class<?>... params) {
    try {
      return getMethod(Class.forName(className), methodName, params);
    } catch (ClassNotFoundException e) {
      throw new LinkageError(String.format("Could not find class %s", className), e);
    }
  }

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
          String.format("method [%s] invocation failed on class [%s].", method.getName(), method.getDeclaringClass()),
          e);
    }
  }

  private ReflectionUtils() {}
}
