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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * MapValue is an abstract representation of an immutable map containing {@link CelValue} as keys
 * and values.
 */
@Immutable(containerOf = {"K", "V"})
@SuppressWarnings(
    "PreferredInterfaceType") // We intentionally store List type to avoid copying on instantiation
public final class ImmutableMapValue<K extends CelValue, V extends CelValue>
    extends MapValue<K, V> {

  @SuppressWarnings("Immutable") // MapValue APIs prohibit mutation.
  private final Map<K, V> originalMap;

  @SuppressWarnings("Immutable") // The value is lazily populated only once via synchronization.
  private volatile ImmutableMap<K, V> cachedImmutableMap = null;

  public static <K extends CelValue, V extends CelValue> ImmutableMapValue<K, V> create(
      Map<K, V> value) {
    return new ImmutableMapValue<>(value);
  }

  private ImmutableMapValue(Map<K, V> originalMap) {
    Preconditions.checkNotNull(originalMap);
    this.originalMap = originalMap;
  }

  @Override
  public ImmutableMap<K, V> value() {
    if (cachedImmutableMap == null) {
      synchronized (this) {
        if (cachedImmutableMap == null) {
          cachedImmutableMap = ImmutableMap.copyOf(originalMap);
        }
      }
    }

    return cachedImmutableMap;
  }

  @Override
  public int size() {
    return originalMap.size();
  }

  @Override
  public boolean isEmpty() {
    return originalMap.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return originalMap.containsKey(key);
  }

  @Override
  public boolean containsValue(Object val) {
    return originalMap.containsValue(val);
  }

  @Override
  public int hashCode() {
    return originalMap.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return originalMap.equals(obj);
  }

  // Note that the following three methods are produced from the immutable map to avoid key/value
  // mutation.
  @Override
  public Set<K> keySet() {
    return value().keySet();
  }

  @Override
  public Collection<V> values() {
    return value().values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return value().entrySet();
  }
}
