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
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.exceptions.CelAttributeNotFoundException;
import dev.cel.common.exceptions.CelInvalidArgumentException;
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
import java.lang.reflect.Array;
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

  private static final ImmutableMap<Class<?>, Object> JAVA_TO_DEFAULT_VALUE_MAP =
      ImmutableMap.<Class<?>, Object>builder()
          .put(boolean.class, false)
          .put(Boolean.class, false)
          .put(String.class, "")
          .put(int.class, 0L)
          .put(Integer.class, 0L)
          .put(long.class, 0L)
          .put(Long.class, 0L)
          .put(UnsignedLong.class, UnsignedLong.ZERO)
          .put(float.class, 0.0)
          .put(Float.class, 0.0)
          .put(double.class, 0.0)
          .put(Double.class, 0.0)
          .put(byte[].class, new byte[0])
          .put(CelByteString.class, CelByteString.EMPTY)
          .put(Duration.class, Duration.ZERO)
          .put(Instant.class, Instant.EPOCH)
          .put(Optional.class, Optional.empty())
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

      if (type.isArray()) {
        TypeToken<?> token = TypeToken.of(genericType);
        TypeToken<?> componentToken =
            Preconditions.checkNotNull(
                token.getComponentType(), "Array component type cannot be null");
        return ListType.create(
            mapJavaTypeToCelType(componentToken.getRawType(), componentToken.getType(), classMap));
      }

      if (type.isInterface()
          && !List.class.isAssignableFrom(type)
          && !Map.class.isAssignableFrom(type)) {
        throw new IllegalArgumentException("Unsupported interface type: " + type.getName());
      }

      TypeToken<?> token = TypeToken.of(genericType);

      if (List.class.isAssignableFrom(type)) {
        Type elementType = ReflectionUtil.resolveGenericParameter(token, List.class, 0);
        return ListType.create(
            mapJavaTypeToCelType(ReflectionUtil.getRawType(elementType), elementType, classMap));
      }

      if (Map.class.isAssignableFrom(type)) {
        Type keyType = ReflectionUtil.resolveGenericParameter(token, Map.class, 0);
        Type valueType = ReflectionUtil.resolveGenericParameter(token, Map.class, 1);

        CelType celKeyType =
            mapJavaTypeToCelType(ReflectionUtil.getRawType(keyType), keyType, classMap);
        if (celKeyType == SimpleType.DOUBLE) {
          throw new IllegalArgumentException("Decimals are not allowed as map keys in CEL.");
        }

        return MapType.create(
            celKeyType,
            mapJavaTypeToCelType(ReflectionUtil.getRawType(valueType), valueType, classMap));
      }

      // Optional is a final class, so reference equality is equivalent to isAssignableFrom
      // but slightly more performant than tree traversal.
      if (type == Optional.class) {
        Type optionalType = ReflectionUtil.resolveGenericParameter(token, Optional.class, 0);
        return OptionalType.create(
            mapJavaTypeToCelType(ReflectionUtil.getRawType(optionalType), optionalType, classMap));
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
        queue.addAll(TypeReferenceCollector.collect(genericPropType));
        compiledGetter = compileGetter(getter);
      } else if (field != null) {
        propType = field.getType();
        genericPropType = field.getGenericType();
        queue.addAll(TypeReferenceCollector.collect(genericPropType));
        compiledGetter = compileFieldGetter(field);
      }

      if (propType != null) {
        Method setter = findSetter(clazz, propName, propType);
        if (setter != null) {
          compiledSetter = compileSetter(setter);
        } else if (field != null
            && !Modifier.isFinal(field.getModifiers())
            && Primitives.wrap(field.getType()) == Primitives.wrap(propType)) {
          compiledSetter = compileFieldSetter(field);
        }
      }

      if (compiledGetter != null) {
        return Optional.of(
            new PropertyAccessor(compiledGetter, compiledSetter, propType, genericPropType));
      }

      return Optional.empty();
    }

    /**
     * Recursively explores a {@link Type} and discovers any transitive, user-defined custom POJO
     * classes nested inside multi-level generic collections, lists, maps, or optionals, collecting
     * them for subsequent properties discovery.
     *
     * <p>"Custom types" are any public non-primitive, non-built-in Java classes that require
     * explicit properties reflective scanning and mapping to a CEL StructType schema (as opposed to
     * standard built-in types like {@code String}, {@code List}, or {@code Map}).
     */
    private static final class TypeReferenceCollector {
      private final Set<Class<?>> collectedTypes = new HashSet<>();

      /**
       * Traverses the given type and returns an immutable set of all custom POJO classes found.
       *
       * @param type The Java type token or parameterized collection type to recursively unpack.
       */
      private static ImmutableSet<Class<?>> collect(Type type) {
        TypeReferenceCollector collector = new TypeReferenceCollector();
        collector.discover(type);
        return ImmutableSet.copyOf(collector.collectedTypes);
      }

      private void discover(Type type) {
        Preconditions.checkNotNull(type, "Type to discover cannot be null.");
        TypeToken<?> token = TypeToken.of(type);
        Class<?> rawType = token.getRawType();

        if (rawType.isArray()) {
          TypeToken<?> componentToken =
              Preconditions.checkNotNull(
                  token.getComponentType(), "Array component type cannot be null");
          discover(componentToken.getType());
          return;
        }

        if (List.class.isAssignableFrom(rawType)) {
          discover(ReflectionUtil.resolveGenericParameter(token, List.class, 0));
          return;
        }

        if (Map.class.isAssignableFrom(rawType)) {
          discover(ReflectionUtil.resolveGenericParameter(token, Map.class, 0));
          discover(ReflectionUtil.resolveGenericParameter(token, Map.class, 1));
          return;
        }

        if (rawType == Optional.class) {
          discover(ReflectionUtil.resolveGenericParameter(token, Optional.class, 0));
          return;
        }

        // Custom types are non-builtin, public classes
        if (!JAVA_TO_DEFAULT_VALUE_MAP.containsKey(rawType)
            && Modifier.isPublic(rawType.getModifiers())) {
          collectedTypes.add(rawType);
        }
      }
    }

    private static Function<Object, Object> compileGetter(Method getter) {
      try {
        // Required to unreflect public getters of package-private classes registered from other
        // packages.
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
        // Required to unreflect public fields of package-private classes registered from other
        // packages.
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
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
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
      if (Modifier.isStatic(method.getModifiers())) {
        return false;
      }
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

    Object getDefaultValue() {
      return getDefaultValue(targetType);
    }

    private static Object getDefaultValue(Class<?> targetType) {
      Object defaultValue = JAVA_TO_DEFAULT_VALUE_MAP.get(targetType);
      if (defaultValue != null) {
        return defaultValue;
      }
      if (List.class.isAssignableFrom(targetType)) {
        return ImmutableList.of();
      }
      if (Map.class.isAssignableFrom(targetType)) {
        return ImmutableMap.of();
      }
      if (targetType.isArray()) {
        return Array.newInstance(targetType.getComponentType(), 0);
      }

      try {
        Constructor<?> constructor = targetType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
      } catch (Exception e) {
        throw new IllegalStateException(
            String.format(
                "Failed to instantiate default instance for uninitialized field of type [%s]. "
                    + "Please ensure the class has a no-argument constructor or is initialized.",
                targetType.getName()),
            e);
      }
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

      Class<?> clazz = value.getClass();
      ImmutableMap<String, PropertyAccessor> accessors = registry.accessorMap.get(clazz);

      if (accessors != null) {
        return new PojoStructValue(value, accessors, registry.classToTypeMap.get(clazz));
      }

      if (clazz.isArray() && clazz != byte[].class) {
        return convertArrayToList(value);
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

      if (value instanceof List) {
        List<?> listValue = (List<?>) value;
        if (List.class.isAssignableFrom(targetType)) {
          return convertListToNative(listValue, targetType, genericType);
        }
        if (targetType.isArray()) {
          return convertListToArray(listValue, targetType, genericType);
        }
      }

      if (Map.class.isAssignableFrom(targetType) && value instanceof Map) {
        return convertMapToNative((Map<?, ?>) value, targetType, genericType);
      }

      return downcastPrimitives(value, targetType);
    }

    // Safe reflection collection cast.
    @SuppressWarnings("unchecked")
    private List<?> convertListToNative(List<?> list, Class<?> targetType, Type genericType) {
      TypeToken<?> token = TypeToken.of(genericType);
      Type elementType = ReflectionUtil.resolveGenericParameter(token, List.class, 0);
      Class<?> componentType = ReflectionUtil.getRawType(elementType);

      boolean isConcreteClass =
          !targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers());

      // Instantiates concrete collection types to prevent ClassCastExceptions.
      // For example, if a POJO field is declared as a concrete implementation like
      // ArrayList<String>, assigning a Guava ImmutableList will fail at runtime due to type
      // mismatch.
      if (isConcreteClass) {
        List<Object> concreteList;
        try {
          concreteList = (List<Object>) targetType.getConstructor().newInstance();
        } catch (Exception e) {
          throw new IllegalStateException(
              "Failed to instantiate concrete collection class for field target type: "
                  + targetType.getName(),
              e);
        }

        for (Object element : list) {
          concreteList.add(toNative(element, componentType, elementType));
        }
        return concreteList;
      }

      ImmutableList.Builder<Object> builder = null;
      for (int i = 0; i < list.size(); i++) {
        Object element = list.get(i);
        Object converted = toNative(element, componentType, elementType);
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
    }

    // Safe reflection collection cast.
    @SuppressWarnings("unchecked")
    private Map<?, ?> convertMapToNative(Map<?, ?> map, Class<?> targetType, Type genericType) {
      TypeToken<?> token = TypeToken.of(genericType);
      Type keyType = ReflectionUtil.resolveGenericParameter(token, Map.class, 0);
      Type valueType = ReflectionUtil.resolveGenericParameter(token, Map.class, 1);
      Class<?> rawKeyType = ReflectionUtil.getRawType(keyType);
      Class<?> rawValueType = ReflectionUtil.getRawType(valueType);

      boolean isConcreteClass =
          !targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers());

      // Instantiates concrete map types to prevent ClassCastExceptions.
      // For example, if a POJO field is declared as a concrete implementation like HashMap<K, V>,
      // assigning a Guava ImmutableMap will fail at runtime due to type mismatch.
      if (isConcreteClass) {
        Map<Object, Object> concreteMap;
        try {
          concreteMap = (Map<Object, Object>) targetType.getConstructor().newInstance();
        } catch (Exception e) {
          throw new IllegalStateException(
              "Failed to instantiate concrete map class for field target type: "
                  + targetType.getName(),
              e);
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
          concreteMap.put(
              toNative(entry.getKey(), rawKeyType, keyType),
              toNative(entry.getValue(), rawValueType, valueType));
        }
        return concreteMap;
      }

      ImmutableMap.Builder<Object, Object> builder = null;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object key = entry.getKey();
        Object val = entry.getValue();
        Object convertedKey = toNative(key, rawKeyType, keyType);
        Object convertedVal = toNative(val, rawValueType, valueType);

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
    }

    private Object convertListToArray(List<?> list, Class<?> targetType, Type genericType) {
      Class<?> componentType = targetType.getComponentType();
      Object array = Array.newInstance(componentType, list.size());
      TypeToken<?> token = TypeToken.of(genericType);
      TypeToken<?> componentToken =
          Preconditions.checkNotNull(
              token.getComponentType(), "Array component type cannot be null");
      Type componentGenericType = componentToken.getType();

      for (int i = 0; i < list.size(); i++) {
        Object element = list.get(i);
        Object converted = toNative(element, componentType, componentGenericType);
        Array.set(array, i, converted);
      }
      return array;
    }

    private ImmutableList<Object> convertArrayToList(Object array) {
      int length = Array.getLength(array);
      ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(length);
      for (int i = 0; i < length; i++) {
        Object element = Array.get(array, i);
        if (element == null) {
          throw new CelInvalidArgumentException(String.format("Element at index %d is null.", i));
        }
        builder.add(toRuntimeValue(element));
      }
      return builder.build();
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
      throw new UnsupportedOperationException(
          "isZeroValue is unsupported for ordinary Java POJOs. Please implement StructValue"
              + " directly on the backing class if zero-value trait support is required.");
    }

    @Override
    public CelType celType() {
      return celType;
    }

    @Override
    public Object select(String field) {
      // Intentionally not proxying `find` here to avoid Optional wrapper allocations.
      PropertyAccessor accessor = accessors.get(field);
      if (accessor != null) {
        Object value = accessor.getValue(instance);
        if (value == null) {
          return accessor.getDefaultValue();
        }
        return value;
      }
      throw CelAttributeNotFoundException.forFieldResolution(field);
    }

    @Override
    public Optional<Object> find(String field) {
      PropertyAccessor accessor = accessors.get(field);
      if (accessor == null) {
        return Optional.empty();
      }
      Object value = accessor.getValue(instance);
      return Optional.ofNullable(value);
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
