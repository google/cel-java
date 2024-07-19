// Copyright 2024 Google LLC
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
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Internal implementation of CEL Set extensions.
 *
 * <p>TODO: https://github.com/google/cel-go/blob/master/ext/sets.go#L127
 *
 * <p>Invoking in operator will result in O(n) complexity. We need to wire in the CEL optimizers to
 * rewrite the AST into a map to achieve a O(1) lookup.
 */
@Immutable
@SuppressWarnings({"unchecked"}) // Unchecked: Type-checker guarantees casting safety.
public final class CelSetsExtensions implements CelCompilerLibrary, CelRuntimeLibrary {

  private static final String SET_CONTAINS_FUNCTION = "sets.contains";
  private static final String SET_CONTAINS_OVERLOAD_DOC =
      "Returns whether the first list argument contains all elements in the second list"
          + " argument. The list may contain elements of any type and standard CEL"
          + " equality is used to determine whether a value exists in both lists. If the"
          + " second list is empty, the result will always return true.";
  private static final String SET_EQUIVALENT_FUNCTION = "sets.equivalent";
  private static final String SET_EQUIVALENT_OVERLOAD_DOC =
      "Returns whether the first and second list are set equivalent. Lists are set equivalent if"
          + " for every item in the first list, there is an element in the second which is equal."
          + " The lists may not be of the same size as they do not guarantee the elements within"
          + " them are unique, so size does not factor intothe computation.";
  private static final String SET_INTERSECTS_FUNCTION = "sets.intersects";
  private static final String SET_INTERSECTS_OVERLOAD_DOC =
      "Returns whether the first and second list intersect. Lists intersect if there is at least"
          + " one element in the first list which is equal to an element in the second list. The"
          + " lists may not be of the same size as they do not guarantee the elements within them"
          + " are unique, so size does not factor into the computation. If either list is empty,"
          + " the result will be false.";

  /** Denotes the set extension function. */
  public enum Function {
    CONTAINS(
        CelFunctionDecl.newFunctionDeclaration(
            SET_CONTAINS_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "list_sets_contains_list",
                SET_CONTAINS_OVERLOAD_DOC,
                SimpleType.BOOL,
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T")))),
        CelRuntime.CelFunctionBinding.from(
            "list_sets_contains_list",
            Collection.class,
            Collection.class,
            CelSetsExtensions::containsAll)),
    EQUIVALENT(
        CelFunctionDecl.newFunctionDeclaration(
            SET_EQUIVALENT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "list_sets_equivalent_list",
                SET_EQUIVALENT_OVERLOAD_DOC,
                SimpleType.BOOL,
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T")))),
        CelRuntime.CelFunctionBinding.from(
            "list_sets_equivalent_list",
            Collection.class,
            Collection.class,
            (listA, listB) -> containsAll(listA, listB) && containsAll(listB, listA))),
    INTERSECTS(
        CelFunctionDecl.newFunctionDeclaration(
            SET_INTERSECTS_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "list_sets_intersects_list",
                SET_INTERSECTS_OVERLOAD_DOC,
                SimpleType.BOOL,
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T")))),
        CelRuntime.CelFunctionBinding.from(
            "list_sets_intersects_list",
            Collection.class,
            Collection.class,
            CelSetsExtensions::setIntersects));

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelRuntime.CelFunctionBinding> functionBindings;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, CelRuntime.CelFunctionBinding... functionBindings) {
      this.functionDecl = functionDecl;
      this.functionBindings = ImmutableSet.copyOf(functionBindings);
    }
  }

  private final ImmutableSet<Function> functions;

  CelSetsExtensions() {
    this(ImmutableSet.copyOf(Function.values()));
  }

  CelSetsExtensions(Set<Function> functions) {
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    functions.forEach(function -> runtimeBuilder.addFunctionBindings(function.functionBindings));
  }

  /**
   * This implementation iterates over the specified collection, checking each element returned by
   * the iterator in turn to see if it's contained in this collection. If all elements are so
   * contained <tt>true</tt> is returned, otherwise <tt>false</tt>.
   *
   * <p>This is picked verbatim as implemented in the Java standard library
   * Collections.containsAll() method.
   *
   * @see #contains(Object)
   */
  private static <T> boolean containsAll(Collection<T> list, Collection<T> subList) {
    for (T e : subList) {
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
   * <p>Source:
   * https://hg.openjdk.org/jdk8u/jdk8u-dev/jdk/file/c5d02f908fb2/src/share/classes/java/util/AbstractCollection.java#l98
   */
  private static <T> boolean contains(Object o, Collection<T> list) {
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
        if (objectsEquals(item, o)) { // TODO: Support Maps.
          return true;
        }
      }
    }
    return false;
  }

  private static boolean objectsEquals(Object o1, Object o2) {
    if (o1 == o2) {
      return true;
    }
    if (o1 == null || o2 == null) {
      return false;
    }
    if (isNumeric(o1) && isNumeric(o2)) {
      if (o1.getClass().equals(o2.getClass())) {
        return o1.equals(o2);
      }
      return ComparisonFunctions.numericEquals((Number) o1, (Number) o2);
    }
    if (isList(o1) && isList(o2)) {
      Collection<?> list1 = (Collection<?>) o1;
      Collection<?> list2 = (Collection<?>) o2;
      if (list1.size() != list2.size()) {
        return false;
      }
      Iterator<?> iterator1 = list1.iterator();
      Iterator<?> iterator2 = list2.iterator();
      boolean result = true;
      while (iterator1.hasNext() && iterator2.hasNext()) {
        Object p1 = iterator1.next();
        Object p2 = iterator2.next();
        result = result && objectsEquals(p1, p2);
      }
      return result;
    }
    return o1.equals(o2);
  }

  private static boolean isNumeric(Object o) {
    return o instanceof Number;
  }

  private static boolean isList(Object o) {
    return o instanceof List;
  }

  private static <T> boolean setIntersects(Collection<T> listA, Collection<T> listB) {
    if (listA.isEmpty() || listB.isEmpty()) {
      return false;
    }
    for (T element : listB) {
      if (contains(element, listA)) {
        return true;
      }
    }
    return false;
  }
}
