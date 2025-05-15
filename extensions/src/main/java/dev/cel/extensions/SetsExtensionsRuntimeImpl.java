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

package dev.cel.extensions;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLiteRuntimeBuilder;
import dev.cel.runtime.CelLiteRuntimeLibrary;
import dev.cel.runtime.RuntimeEquality;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

@Immutable
final class SetsExtensionsRuntimeImpl implements CelLiteRuntimeLibrary {
  private final RuntimeEquality runtimeEquality;

  private final ImmutableSet<SetsFunction> functions;

  SetsExtensionsRuntimeImpl(RuntimeEquality runtimeEquality, Set<SetsFunction> functions) {
    this.runtimeEquality = runtimeEquality;
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public void setRuntimeOptions(CelLiteRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(newFunctionBindings());
  }

  ImmutableSet<CelFunctionBinding> newFunctionBindings() {
    ImmutableSet.Builder<CelFunctionBinding> bindingBuilder = ImmutableSet.builder();
    for (SetsFunction function : functions) {
      switch (function) {
        case CONTAINS:
          bindingBuilder.add(
              CelFunctionBinding.from(
                  "list_sets_contains_list",
                  Collection.class,
                  Collection.class,
                  this::containsAll));
          break;
        case EQUIVALENT:
          bindingBuilder.add(
              CelFunctionBinding.from(
                  "list_sets_equivalent_list",
                  Collection.class,
                  Collection.class,
                  (listA, listB) -> containsAll(listA, listB) && containsAll(listB, listA)));
          break;
        case INTERSECTS:
          bindingBuilder.add(
              CelFunctionBinding.from(
                  "list_sets_intersects_list",
                  Collection.class,
                  Collection.class,
                  this::setIntersects));
          break;
      }
    }

    return bindingBuilder.build();
  }

  /**
   * This implementation iterates over the specified collection, checking each element returned by
   * the iterator in turn to see if it's contained in this collection. If all elements are so
   * contained <tt>true</tt> is returned, otherwise <tt>false</tt>.
   *
   * <p>This is picked verbatim as implemented in the Java standard library
   * Collections.containsAll() method.
   *
   * @see #contains(Object, Collection)
   */
  private boolean containsAll(Collection<?> list, Collection<?> subList) {
    for (Object e : subList) {
      if (!contains(e, list)) {
        return false;
      }
    }
    return true;
  }

  /**
   * This implementation iterates over the elements in the collection, checking each element in turn
   * for equality with the specified element.
   *
   * <p>This is picked verbatim as implemented in the Java standard library Collections.contains()
   * method.
   *
   * <p>Source: <a
   * href="https://hg.openjdk.org/jdk8u/jdk8u-dev/jdk/file/c5d02f908fb2/src/share/classes/java/util/AbstractCollection.java#l98">OpenJDK
   * AbstractCollection<a>
   */
  private boolean contains(Object o, Collection<?> list) {
    Iterator<?> it = list.iterator();
    if (o == null) {
      while (it.hasNext()) {
        if (it.next() == null) {
          return true;
        }
      }
    } else {
      while (it.hasNext()) {
        Object item = it.next();
        if (objectsEquals(item, o)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean objectsEquals(Object o1, Object o2) {
    return runtimeEquality.objectEquals(o1, o2);
  }

  private boolean setIntersects(Collection<?> listA, Collection<?> listB) {
    if (listA.isEmpty() || listB.isEmpty()) {
      return false;
    }
    for (Object element : listB) {
      if (contains(element, listA)) {
        return true;
      }
    }
    return false;
  }
}
