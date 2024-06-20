// Copyright 2023 Google LLC
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

package dev.cel.common.values;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.DoNotCall;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.types.CelType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * MapValue is an abstract representation of a generic map containing {@link CelValue} as keys and
 * values.
 *
 * <p>All methods that can mutate the map are disallowed.
 */
@Immutable(containerOf = {"K", "V"})
public abstract class MapValue<K extends CelValue, V extends CelValue> extends CelValue
    implements Map<K, V>, SelectableValue<K> {

  private static final MapType MAP_TYPE = MapType.create(SimpleType.DYN, SimpleType.DYN);

  @Override
  public abstract Map<K, V> value();

  @Override
  public boolean isZeroValue() {
    return isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public V get(Object key) {
    return select((K) key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public V select(K field) {
    return (V)
        find(field)
            .orElseThrow(
                () ->
                    new CelRuntimeException(
                        new IllegalArgumentException(
                            String.format("key '%s' is not present in map.", field.value())),
                        CelErrorCode.ATTRIBUTE_NOT_FOUND));
  }

  @Override
  public Optional<CelValue> find(K field) {
    return value().containsKey(field) ? Optional.of(value().get(field)) : Optional.empty();
  }

  @Override
  public CelType celType() {
    return MAP_TYPE;
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V putIfAbsent(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean replace(K key, V oldValue, V newValue) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V replace(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends @Nullable V> remappingFunction) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V compute(
      K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> remappingFunction) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V merge(
      K key, V value, BiFunction<? super V, ? super V, ? extends @Nullable V> function) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void putAll(Map<? extends K, ? extends V> map) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V remove(Object o) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void clear() {
    throw new UnsupportedOperationException();
  }
}
