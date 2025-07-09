// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License aj
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.runtime;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A custom list view implementation that allows O(1) concatenation of two lists. Its primary
 * purpose is to facilitate efficient accumulation of lists for later materialization. (ex:
 * comprehensions that dispatch `add_list` to concat N lists together).
 *
 * <p>This does not support any of the standard list operations from {@link java.util.List}.
 */
final class ConcatenatedListView<E> extends AbstractList<E> {
  private final List<List<? extends E>> sourceLists;
  private int totalSize = 0;

  ConcatenatedListView() {
    this.sourceLists = new ArrayList<>();
  }

  ConcatenatedListView(Collection<? extends E> collection) {
    this();
    addAll(collection);
  }

  @Override
  public boolean addAll(Collection<? extends E> collection) {
    if (!(collection instanceof List)) {
      // size() is O(1) iff it's a list
      throw new IllegalStateException("addAll must be called with lists, not collections");
    }

    sourceLists.add((List<? extends E>) collection);
    totalSize += collection.size();
    return true;
  }

  @Override
  public E get(int index) {
    throw new UnsupportedOperationException("get method not supported.");
  }

  @Override
  public int size() {
    return totalSize;
  }

  @Override
  public Iterator<E> iterator() {
    return new ConcatenatingIterator();
  }

  /** Custom iterator to provide a flat view of all concatenated collections */
  private class ConcatenatingIterator implements Iterator<E> {
    private int index = 0;
    private Iterator<? extends E> iterator = null;

    @Override
    public boolean hasNext() {
      while (iterator == null || !iterator.hasNext()) {
        if (index < sourceLists.size()) {
          iterator = sourceLists.get(index).iterator();
          index++;
        } else {
          return false;
        }
      }
      return true;
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return iterator.next();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove method not supported");
    }
  }
}
