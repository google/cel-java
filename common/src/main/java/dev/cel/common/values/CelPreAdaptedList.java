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

package dev.cel.common.values;

import dev.cel.common.annotations.Internal;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * A zero-allocation view over a list we know is already adapted.
 *
 * <p>This class purely exists as an optimization scheme to avoid redundant collection traversals
 * in {@link CelValueConverter}, and is not intended for general use.
 */
@Internal
final class CelPreAdaptedList<E> extends AbstractList<E>
    implements RandomAccess {
  private final List<E> delegate;

  private CelPreAdaptedList(List<E> delegate) {
    this.delegate = delegate;
  }

  static <E> CelPreAdaptedList<E> wrap(List<E> safeList) {
    return new CelPreAdaptedList<>(safeList);
  }

  @Override
  public E get(int index) {
    return delegate.get(index);
  }

  @Override
  public int size() {
    return delegate.size();
  }
}
