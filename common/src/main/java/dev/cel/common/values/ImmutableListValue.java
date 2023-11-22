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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * ImmutableListValue is a representation of an immutable list containing zero or more {@link
 * CelValue}.
 */
@Immutable
@SuppressWarnings(
    "PreferredInterfaceType") // We intentionally store List type to avoid copying on instantiation
public final class ImmutableListValue<E extends CelValue> extends ListValue<E> {

  @SuppressWarnings("Immutable") // ListValue APIs prohibit mutation.
  private final List<E> originalList;

  @SuppressWarnings("Immutable") // The value is lazily populated only once via synchronization.
  private volatile ImmutableList<E> cachedImmutableList = null;

  public static <E extends CelValue> ImmutableListValue<E> create(List<E> value) {
    return new ImmutableListValue<>(value);
  }

  private ImmutableListValue(List<E> originalList) {
    this.originalList = ImmutableList.copyOf(originalList);
  }

  @Override
  public ImmutableList<E> value() {
    if (cachedImmutableList == null) {
      synchronized (this) {
        if (cachedImmutableList == null) {
          cachedImmutableList = ImmutableList.copyOf(originalList);
        }
      }
    }

    return cachedImmutableList;
  }

  @Override
  public int size() {
    return originalList.size();
  }

  @Override
  public boolean isEmpty() {
    return originalList.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return originalList.contains(o);
  }

  @Override
  public Iterator<E> iterator() {
    return originalList.iterator();
  }

  @Override
  public Object[] toArray() {
    return originalList.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return originalList.toArray(a);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return originalList.containsAll(c);
  }

  @Override
  public E get(int index) {
    return originalList.get(index);
  }

  @Override
  public int indexOf(Object o) {
    return originalList.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return originalList.lastIndexOf(o);
  }

  @Override
  public ListIterator<E> listIterator() {
    return originalList.listIterator();
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    return originalList.listIterator(index);
  }

  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    return originalList.subList(fromIndex, toIndex);
  }

  @Override
  public int hashCode() {
    return originalList.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return originalList.equals(obj);
  }
}
