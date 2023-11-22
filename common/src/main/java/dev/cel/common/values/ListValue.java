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
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.nullness.Nullable;

/**
 * ListValue is an abstract representation of a generic list containing zero or more {@link
 * CelValue}.
 *
 * <p>All methods that can mutate the list are disallowed.
 */
@Immutable
public abstract class ListValue<E extends CelValue> extends CelValue implements List<E> {
  private static final ListType LIST_TYPE = ListType.create(SimpleType.DYN);

  @Override
  @SuppressWarnings("Immutable") // ListValue APIs prohibit mutation.
  public abstract List<E> value();

  @Override
  public boolean isZeroValue() {
    return isEmpty();
  }

  @Override
  public CelType celType() {
    return LIST_TYPE;
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
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

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean retainAll(Collection<?> c) {
    return false;
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final E set(int index, E element) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void replaceAll(UnaryOperator<E> operator) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void sort(@Nullable Comparator<? super E> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void add(int index, E element) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean add(E e) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean addAll(int index, Collection<? extends E> newElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final E remove(int index) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }
}
