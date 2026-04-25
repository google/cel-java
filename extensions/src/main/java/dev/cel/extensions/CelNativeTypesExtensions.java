// Copyright 2026 Google LLC
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

package dev.cel.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.exceptions.CelAttributeNotFoundException;
import dev.cel.common.internal.ReflectionUtil;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.StructValue;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Extension for supporting native Java types (POJOs) in CEL.
 *
 * <p>This allows seamless plugin and evaluation of message creations and field selections without
 * involving protobuf.
 */
@Immutable
public final class CelNativeTypesExtensions implements CelCompilerLibrary, CelRuntimeLibrary {

  private final NativeTypeRegistry registry;

  // Set of all standard java.lang.Object method names.
  private static final ImmutableSet<String> OBJECT_METHOD_NAMES =
      stream(Object.class.getDeclaredMethods()).map(Method::getName).collect(toImmutableSet());

  private static final ImmutableMap<Class<?>, CelType> JAVA_TO_CEL_TYPE_MAP =
      ImmutableMap.<Class<?>, CelType>builder()
          .put(boolean.class, SimpleType.BOOL)
          .put(Boolean.class, SimpleType.BOOL)
          .put(String.class, SimpleType.STRING)
          .put(int.class, SimpleType.INT)
          .put(Integer.class, SimpleType.INT)
          .put(long.class, SimpleType.INT)
          .put(Long.class, SimpleType.INT)
          .put(UnsignedLong.class, SimpleType.UINT)
          .put(float.class, SimpleType.DOUBLE)
          .put(Float.class, SimpleType.DOUBLE)
          .put(double.class, SimpleType.DOUBLE)
          .put(Double.class, SimpleType.DOUBLE)
          .put(byte[].class, SimpleType.BYTES)
          .put(CelByteString.class, SimpleType.BYTES)
          .put(Duration.class, SimpleType.DURATION)
          .put(Instant.class, SimpleType.TIMESTAMP)
          .put(Object.class, SimpleType.DYN)
          .buildOrThrow();

  /** Creates a new instance of {@link CelNativeTypesExtensions} for the given classes. */
  static CelNativeTypesExtensions nativeTypes(Class<?>... classes) {
    return new CelNativeTypesExtensions(new NativeTypeRegistry(NativeTypeScanner.scan(classes)));
  }

  @VisibleForTesting
  NativeTypeRegistry getRegistry() {
    return registry;
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.setValueProvider(registry);
    runtimeBuilder.setTypeProvider(registry);
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    checkerBuilder.setTypeProvider(registry);
  }

  /**
   * NativeTypeScanner scans registered Java classes to extract properties and compile accessors.
   */
  @VisibleForTesting
  static final class NativeTypeScanner {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private NativeTypeScanner() {}

    private static final class ScanResult {
      private final ImmutableMap<String, Class<?>> classMap;
      private final ImmutableMap<String, StructType> typeMap;
      private final ImmutableMap<Class<?>, StructType> classToTypeMap;
      private final ImmutableMap<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMap;

      ScanResult(
          ImmutableMap<String, Class<?>> classMap,
          ImmutableMap<String, StructType> typeMap,
          ImmutableMap<Class<?>, StructType> classToTypeMap,
          ImmutableMap<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMap) {
        this.classMap = classMap;
        this.typeMap = typeMap;
        this.classToTypeMap = classToTypeMap;
        this.accessorMap = accessorMap;
      }
    }

    private static ScanResult scan(Class<?>... classes) {
      ImmutableMap.Builder<String, Class<?>> classMapBuilder = ImmutableMap.builder();
      ImmutableMap.Builder<String, StructType> typeMapBuilder = ImmutableMap.builder();
      ImmutableMap.Builder<Class<?>, StructType> classToTypeMapBuilder = ImmutableMap.builder();
      ImmutableMap.Builder<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMapBuilder =
          ImmutableMap.builder();

      Set<Class<?>> visited = new HashSet<>();
      Queue<Class<?>> queue = new ArrayDeque<>(Arrays.asList(classes));

      while (!queue.isEmpty()) {
        Class<?> clazz = queue.poll();
        if (shouldSkip(clazz, visited)) {
          continue;
        }
        visited.add(clazz);

        String typeName = getCelTypeName(clazz);
        classMapBuilder.put(typeName, clazz);

        ImmutableMap<String, PropertyAccessor> accessors = scanProperties(clazz, queue);
        accessorMapBuilder.put(clazz, accessors);
      }

      ImmutableMap<String, Class<?>> classMap = classMapBuilder.buildOrThrow();
      ImmutableMap<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMap =
          accessorMapBuilder.buildOrThrow();

      for (Map.Entry<String, Class<?>> entry : classMap.entrySet()) {
        String typeName = entry.getKey();
        Class<?> clazz = entry.getValue();

        StructType structType = createStructType(clazz, classMap, accessorMap);
        typeMapBuilder.put(typeName, structType);
        classToTypeMapBuilder.put(clazz, structType);
      }

      ScanResult result =
          new ScanResult(
              classMap,
              typeMapBuilder.buildOrThrow(),
              classToTypeMapBuilder.buildOrThrow(),
              accessorMap);

      validateRegisteredClasses(result.classToTypeMap, result.classMap, result.accessorMap);

      return result;
    }

    private static void validateRegisteredClasses(
        ImmutableMap<Class<?>, StructType> classToTypeMap,
        ImmutableMap<String, Class<?>> classMap,
        ImmutableMap<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMap) {
      for (Class<?> clazz : classToTypeMap.keySet()) {
        for (String prop : getProperties(clazz)) {
          try {
            getPropertyType(clazz, prop, classMap, accessorMap);
          } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unsupported type for property '" + prop + "' in class " + clazz.getName(), e);
          }
        }
      }
    }

    private static boolean shouldSkip(Class<?> clazz, Set<Class<?>> visited) {
      return clazz == null
          || visited.contains(clazz)
          || clazz.isInterface()
          || isSupportedType(clazz);
    }

    private static boolean isSupportedType(Class<?> type) {
      return JAVA_TO_CEL_TYPE_MAP.containsKey(type)
          || type == Optional.class
          || List.class.isAssignableFrom(type)
          || Map.class.isAssignableFrom(type)
          || type.isArray();
    }

    private static StructType createStructType(
        Class<?> clazz,
        ImmutableMap<String, Class<?>> classMap,
        ImmutableMap<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMap) {
      return StructType.create(
          getCelTypeName(clazz),
          getProperties(clazz),
          fieldName -> Optional.of(getPropertyType(clazz, fieldName, classMap, accessorMap)));
    }

    private static CelType getPropertyType(
        Class<?> clazz,
        String propertyName,
        ImmutableMap<String, Class<?>> classMap,
        ImmutableMap<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMap) {
      ImmutableMap<String, PropertyAccessor> accessors = accessorMap.get(clazz);
      if (accessors != null) {
        PropertyAccessor accessor = accessors.get(propertyName);
        if (accessor != null) {
          return mapJavaTypeToCelType(accessor.targetType, accessor.genericTargetType, classMap);
        }
      }
      throw new IllegalArgumentException("No public field or getter for " + propertyName);
    }

    private static CelType mapJavaTypeToCelType(
        Class<?> type, Type genericType, ImmutableMap<String, Class<?>> classMap) {

      CelType celType = JAVA_TO_CEL_TYPE_MAP.get(type);
      if (celType != null) {
        return celType;
      }

      if (type.isInterface()
          && !List.class.isAssignableFrom(type)
          && !Map.class.isAssignableFrom(type)) {
        throw new IllegalArgumentException("Unsupported interface type: " + type.getName());
      }

      if (List.class.isAssignableFrom(type)) {
        return ReflectionUtil.getTypeArguments(genericType, 1)
            .map(
                args ->
                    ListType.create(
                        mapJavaTypeToCelType(
                            ReflectionUtil.getRawType(args[0]), args[0], classMap)))
            .orElse(ListType.create(SimpleType.DYN));
      }

      if (Map.class.isAssignableFrom(type)) {
        return ReflectionUtil.getTypeArguments(genericType, 2)
            .map(
                args -> {
                  Class<?> keyType = ReflectionUtil.getRawType(args[0]);
                  Class<?> valueType = ReflectionUtil.getRawType(args[1]);

                  CelType celKeyType = mapJavaTypeToCelType(keyType, args[0], classMap);
                  if (celKeyType == SimpleType.DOUBLE) {
                    throw new IllegalArgumentException(
                        "Decimals are not allowed as map keys in CEL.");
                  }

                  return MapType.create(
                      celKeyType, mapJavaTypeToCelType(valueType, args[1], classMap));
                })
            .orElse(MapType.create(SimpleType.DYN, SimpleType.DYN));
      }

      if (type.equals(Optional.class)) {
        return ReflectionUtil.getTypeArguments(genericType, 1)
            .map(
                args ->
                    OptionalType.create(
                        mapJavaTypeToCelType(
                            ReflectionUtil.getRawType(args[0]), args[0], classMap)))
            .orElse(OptionalType.create(SimpleType.DYN));
      }

      String typeName = getCelTypeName(type);
      if (classMap.containsKey(typeName)) {
        return StructTypeReference.create(typeName);
      }

      throw new IllegalArgumentException(
          "Unsupported Java type for CEL mapping: " + type.getName());
    }

    private static ImmutableMap<String, PropertyAccessor> scanProperties(
        Class<?> clazz, Queue<Class<?>> queue) {
      ImmutableMap.Builder<String, PropertyAccessor> builtAccessors = ImmutableMap.builder();

      for (String propName : getProperties(clazz)) {
        buildPropertyAccessor(clazz, propName, queue)
            .ifPresent(accessor -> builtAccessors.put(propName, accessor));
      }

      return builtAccessors.buildOrThrow();
    }

    private static Optional<PropertyAccessor> buildPropertyAccessor(
        Class<?> clazz, String propName, Queue<Class<?>> queue) {
      Method getter = findGetter(clazz, propName);
      Field field = findField(clazz, propName);

      Class<?> propType = null;
      Type genericPropType = null;
      Function<Object, Object> compiledGetter = null;
      BiConsumer<Object, Object> compiledSetter = null;

      if (getter != null) {
        propType = getter.getReturnType();
        genericPropType = getter.getGenericReturnType();
        Class<?> elemType = ReflectionUtil.getElementType(propType, genericPropType);
        if (Modifier.isPublic(elemType.getModifiers())) {
          queue.add(elemType);
        }
        compiledGetter = compileGetter(getter);
      } else if (field != null) {
        Class<?> elemType = ReflectionUtil.getElementType(field.getType(), field.getGenericType());
        if (Modifier.isPublic(elemType.getModifiers())) {
          queue.add(elemType);
        }
        propType = field.getType();
        genericPropType = field.getGenericType();
        compiledGetter = compileFieldGetter(field);
      }

      if (propType != null) {
        Method setter = findSetter(clazz, propName, propType);
        if (setter != null) {
          compiledSetter = compileSetter(setter);
        } else if (field != null && !Modifier.isFinal(field.getModifiers())) {
          compiledSetter = compileFieldSetter(field);
        }
      }

      if (compiledGetter != null) {
        return Optional.of(
            new PropertyAccessor(compiledGetter, compiledSetter, propType, genericPropType));
      }

      return Optional.empty();
    }

    private static Function<Object, Object> compileGetter(Method getter) {
      try {
        getter.setAccessible(true);
        MethodHandle mh = LOOKUP.unreflect(getter);
        return instance -> {
          try {
            return mh.invoke(instance);
          } catch (Throwable t) {
            throw new IllegalStateException("Failed to invoke getter for " + getter, t);
          }
        };
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Failed to unreflect getter", e);
      }
    }

    private static Function<Object, Object> compileFieldGetter(Field field) {
      try {
        field.setAccessible(true);
        MethodHandle mh = LOOKUP.unreflectGetter(field);
        return instance -> {
          try {
            return mh.invoke(instance);
          } catch (Throwable t) {
            throw new IllegalStateException("Failed to get field " + field, t);
          }
        };
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Failed to access field " + field, e);
      }
    }

    private static BiConsumer<Object, Object> compileSetter(Method setter) {
      try {
        setter.setAccessible(true);
        MethodHandle mh = LOOKUP.unreflect(setter);
        return (instance, value) -> {
          try {
            mh.invoke(instance, value);
          } catch (Throwable t) {
            throw new IllegalStateException("Failed to invoke setter for " + setter, t);
          }
        };
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Failed to unreflect setter", e);
      }
    }

    private static BiConsumer<Object, Object> compileFieldSetter(Field field) {
      try {
        field.setAccessible(true);
        MethodHandle mh = LOOKUP.unreflectSetter(field);
        return (instance, value) -> {
          try {
            mh.invoke(instance, value);
          } catch (Throwable t) {
            throw new IllegalStateException("Failed to set field " + field, t);
          }
        };
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Failed to access field " + field, e);
      }
    }

    private static @Nullable Method findGetter(Class<?> clazz, String propertyName) {
      String getterName = buildMethodName("get", propertyName);
      String isGetterName = buildMethodName("is", propertyName);

      Method isGetter = null;
      Method prefixLess = null;

      for (Method method : clazz.getMethods()) {
        if (method.isBridge() || method.isSynthetic()) {
          // Ignore compiler-generated duplicates
          continue;
        }
        if (method.getParameterCount() == 0) {
          String name = method.getName();
          if (name.equals(getterName)) {
            return method;
          }
          if (name.equals(isGetterName)) {
            isGetter = method;
          }
          if (name.equals(propertyName)) {
            prefixLess = method;
          }
        }
      }

      if (isGetter != null) {
        return isGetter;
      }
      return prefixLess;
    }

    private static @Nullable Field findField(Class<?> clazz, String propertyName) {
      for (Field field : clazz.getFields()) {
        if (field.getName().equals(propertyName)) {
          return field;
        }
      }
      return null;
    }

    private static @Nullable Method findSetter(
        Class<?> clazz, String propertyName, Class<?> propertyType) {
      String setterName = buildMethodName("set", propertyName);
      return stream(clazz.getMethods())
          .filter(m -> !m.isBridge() && !m.isSynthetic())
          .filter(m -> m.getName().equals(setterName))
          .filter(m -> m.getParameterCount() == 1)
          .filter(m -> m.getParameterTypes()[0].equals(propertyType))
          .findFirst()
          .orElse(null);
    }

    private static Set<String> getAllDeclaredFieldNames(Class<?> clazz) {
      Set<String> declaredFieldNames = new HashSet<>();
      Class<?> currentClass = clazz;
      while (currentClass != null) {
        for (Field field : currentClass.getDeclaredFields()) {
          declaredFieldNames.add(field.getName());
        }
        currentClass = currentClass.getSuperclass();
      }
      return declaredFieldNames;
    }

    @VisibleForTesting
    static ImmutableSet<String> getProperties(Class<?> clazz) {
      ImmutableSet.Builder<String> properties = ImmutableSet.builder();
      Set<String> declaredFieldNames = getAllDeclaredFieldNames(clazz);
      for (Field field : clazz.getFields()) {
        properties.add(field.getName());
      }
      for (Method method : clazz.getMethods()) {
        if (isGetter(method)) {
          String propName = getPropertyName(method);
          if (method.getName().startsWith("get") || method.getName().startsWith("is")) {
            properties.add(propName);
          } else if (declaredFieldNames.contains(propName)) {
            properties.add(propName);
          }
        }
      }
      return properties.build();
    }

    private static boolean isGetter(Method method) {
      if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
        return false;
      }
      if (method.getReturnType() == void.class) {
        return false;
      }
      String name = method.getName();
      if (OBJECT_METHOD_NAMES.contains(name)) {
        return false;
      }
      if (name.startsWith("get")) {
        return name.length() > 3;
      }
      if (name.startsWith("is")) {
        return name.length() > 2 && Primitives.wrap(method.getReturnType()) == Boolean.class;
      }
      return true;
    }

    private static String decapitalize(String name) {
      Preconditions.checkArgument(name != null && !name.isEmpty());
      if (name.length() > 1
          && Character.isUpperCase(name.charAt(1))
          && Character.isUpperCase(name.charAt(0))) {
        return name;
      }
      char[] chars = name.toCharArray();
      chars[0] = Character.toLowerCase(chars[0]);
      return new String(chars);
    }

    private static String getPropertyName(Method method) {
      String name = method.getName();
      if (name.startsWith("get")) {
        return decapitalize(name.substring(3));
      }
      if (name.startsWith("is")) {
        return decapitalize(name.substring(2));
      }
      if (name.startsWith("set")) {
        return decapitalize(name.substring(3));
      }
      return name;
    }

    private static String capitalize(String name) {
      return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String buildMethodName(String prefix, String propertyName) {
      return prefix + capitalize(propertyName);
    }
  }

  /**
   * NativeTypeRegistry holds the state produced by NativeTypeScanner and acts as a CelValueProvider
   * and CelTypeProvider for the CEL runtime.
   */
  @VisibleForTesting
  @Immutable
  static final class NativeTypeRegistry implements CelValueProvider, CelTypeProvider {

    private final ImmutableMap<String, Class<?>> classMap;
    private final ImmutableMap<String, StructType> typeMap;
    private final ImmutableMap<Class<?>, StructType> classToTypeMap;
    private final ImmutableMap<Class<?>, ImmutableMap<String, PropertyAccessor>> accessorMap;
    private final NativeValueConverter converter;

    private NativeTypeRegistry(NativeTypeScanner.ScanResult scanResult) {
      this.classMap = scanResult.classMap;
      this.typeMap = scanResult.typeMap;
      this.classToTypeMap = scanResult.classToTypeMap;
      this.accessorMap = scanResult.accessorMap;
      this.converter = new NativeValueConverter(this);
    }

    @Override
    public ImmutableList<CelType> types() {
      return ImmutableList.copyOf(typeMap.values());
    }

    @Override
    public Optional<CelType> findType(String typeName) {
      return Optional.ofNullable(typeMap.get(typeName));
    }

    @Override
    public Optional<Object> newValue(String typeName, Map<String, Object> fields) {
      Class<?> clazz = classMap.get(typeName);
      if (clazz == null) {
        return Optional.empty();
      }

      try {
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();
        ImmutableMap<String, PropertyAccessor> accessors = accessorMap.get(clazz);

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
          PropertyAccessor accessor = accessors.get(entry.getKey());
          if (accessor == null) {
            throw new IllegalArgumentException(
                "Unknown field: " + entry.getKey() + " for type " + typeName);
          }
          Object value =
              converter.toNative(entry.getValue(), accessor.targetType, accessor.genericTargetType);
          accessor.setValue(instance, value);
        }

        StructType structType = typeMap.get(typeName);
        return Optional.of(new PojoStructValue(instance, accessors, structType));
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "Failed to create instance of "
                + typeName
                + ": No public no-argument constructor found.",
            e);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to create instance of " + typeName, e);
      }
    }

    @Override
    public CelValueConverter celValueConverter() {
      return this.converter;
    }
  }

  /**
   * PropertyAccessor holds the compiled getter and setter for a property, along with its type
   * information.
   */
  @Immutable
  @SuppressWarnings("Immutable")
  private static final class PropertyAccessor {
    private final Function<Object, Object> getter;
    private final @Nullable BiConsumer<Object, Object> setter;
    private final Class<?> targetType;
    private final @Nullable Type genericTargetType;

    private PropertyAccessor(
        Function<Object, Object> getter,
        @Nullable BiConsumer<Object, Object> setter,
        Class<?> targetType,
        @Nullable Type genericTargetType) {
      this.getter = getter;
      this.setter = setter;
      this.targetType = targetType;
      this.genericTargetType = genericTargetType;
    }

    Object getValue(Object instance) {
      return getter.apply(instance);
    }

    void setValue(Object instance, Object value) {
      if (setter != null) {
        setter.accept(instance, value);
      } else {
        throw new IllegalStateException("No setter found for property");
      }
    }
  }

  /** NativeValueConverter handles conversion between Java objects and CEL values. */
  @Immutable
  private static final class NativeValueConverter extends CelValueConverter {

    private final NativeTypeRegistry registry;

    private NativeValueConverter(NativeTypeRegistry registry) {
      this.registry = registry;
    }

    @Override
    public Object toRuntimeValue(Object value) {
      if (value instanceof CelValue) {
        return super.toRuntimeValue(value);
      }

      if (registry.classToTypeMap.containsKey(value.getClass())) {
        return new PojoStructValue(
            value,
            registry.accessorMap.get(value.getClass()),
            registry.classToTypeMap.get(value.getClass()));
      }

      return super.toRuntimeValue(value);
    }

    Object toNative(Object value, Class<?> targetType, Type genericType) {
      if (value instanceof CelValue && !StructValue.class.isAssignableFrom(targetType)) {
        value = super.maybeUnwrap(value);
      }
      if (targetType == Optional.class) {
        if (value instanceof Optional) {
          return value;
        }
        return Optional.ofNullable(value);
      }
      if (targetType == UnsignedLong.class) {
        if (value instanceof UnsignedLong) {
          return value;
        }
      }
      if (targetType == byte[].class && value instanceof CelByteString) {
        return ((CelByteString) value).toByteArray();
      }

      if (List.class.isAssignableFrom(targetType) && value instanceof List) {
        return convertListToNative((List<?>) value, genericType);
      }

      if (Map.class.isAssignableFrom(targetType) && value instanceof Map) {
        return convertMapToNative((Map<?, ?>) value, genericType);
      }

      return downcastPrimitives(value, targetType);
    }

    private Object convertListToNative(List<?> list, Type genericType) {
      return ReflectionUtil.getTypeArguments(genericType, 1)
          .map(
              args -> {
                Class<?> componentType = ReflectionUtil.getRawType(args[0]);
                ImmutableList.Builder<Object> builder = null;
                for (int i = 0; i < list.size(); i++) {
                  Object element = list.get(i);
                  Object converted = toNative(element, componentType, args[0]);
                  if (!Objects.equals(converted, element) && builder == null) {
                    builder = ImmutableList.builderWithExpectedSize(list.size());
                    for (int j = 0; j < i; j++) {
                      builder.add(list.get(j));
                    }
                  }
                  if (builder != null) {
                    builder.add(converted);
                  }
                }

                if (builder == null) {
                  return list;
                }
                return builder.build();
              })
          .orElse(list);
    }

    private Object convertMapToNative(Map<?, ?> map, Type genericType) {
      return ReflectionUtil.getTypeArguments(genericType, 2)
          .map(
              args -> {
                Class<?> keyType = ReflectionUtil.getRawType(args[0]);
                Class<?> valueType = ReflectionUtil.getRawType(args[1]);

                ImmutableMap.Builder<Object, Object> builder = null;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                  Object key = entry.getKey();
                  Object val = entry.getValue();
                  Object convertedKey = toNative(key, keyType, args[0]);
                  Object convertedVal = toNative(val, valueType, args[1]);

                  if ((!Objects.equals(convertedKey, key) || !Objects.equals(convertedVal, val))
                      && builder == null) {
                    builder = ImmutableMap.builderWithExpectedSize(map.size());
                    for (Map.Entry<?, ?> prevEntry : map.entrySet()) {
                      if (Objects.equals(prevEntry.getKey(), entry.getKey())) {
                        break;
                      }
                      builder.put(prevEntry.getKey(), prevEntry.getValue());
                    }
                  }

                  if (builder != null) {
                    builder.put(convertedKey, convertedVal);
                  }
                }

                if (builder == null) {
                  return map;
                }
                return builder.buildOrThrow();
              })
          .orElse(map);
    }

    private Object downcastPrimitives(Object value, Class<?> targetType) {
      Class<?> wrappedTargetType = Primitives.wrap(targetType);
      if (wrappedTargetType == Integer.class && value instanceof Long) {
        return ((Long) value).intValue();
      }
      if (wrappedTargetType == Float.class && value instanceof Double) {
        return ((Double) value).floatValue();
      }

      return value;
    }
  }

  /** PojoStructValue represents a native Java object as a CEL struct value. */
  @SuppressWarnings("Immutable")
  private static final class PojoStructValue extends StructValue<String, Object> {
    private final Object instance;
    private final ImmutableMap<String, PropertyAccessor> accessors;
    private final StructType celType;

    private PojoStructValue(
        Object instance, ImmutableMap<String, PropertyAccessor> accessors, StructType celType) {
      this.instance = instance;
      this.accessors = accessors;
      this.celType = celType;
    }

    @Override
    public Object value() {
      return instance;
    }

    @Override
    public boolean isZeroValue() {
      return false;
    }

    @Override
    public CelType celType() {
      return celType;
    }

    @Override
    public Object select(String field) {
      PropertyAccessor accessor = accessors.get(field);
      if (accessor != null) {
        return accessor.getValue(instance);
      }
      throw CelAttributeNotFoundException.forFieldResolution(field);
    }

    @Override
    public Optional<Object> find(String field) {
      return Optional.ofNullable(accessors.get(field)).map(accessor -> accessor.getValue(instance));
    }
  }

  private static String getCelTypeName(Class<?> clazz) {
    String canonicalName = clazz.getCanonicalName();
    if (canonicalName == null) {
      throw new IllegalArgumentException(
          "Cannot get canonical name for class: "
              + clazz.getName()
              + ". Anonymous or local classes are not supported.");
    }
    return canonicalName;
  }

  private CelNativeTypesExtensions(NativeTypeRegistry registry) {
    this.registry = registry;
  }
}
