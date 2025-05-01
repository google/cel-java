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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Collection;
import java.util.Set;

/** Internal implementation of CEL lists extensions. */
final class CelListsExtensions implements CelCompilerLibrary, CelRuntimeLibrary {

  private static final TypeParamType LIST_PARAM_TYPE = TypeParamType.create("T");

  @SuppressWarnings({"unchecked"}) // Unchecked: Type-checker guarantees casting safety.
  public enum Function {
    FLATTEN(
        CelFunctionDecl.newFunctionDeclaration(
            "flatten",
            CelOverloadDecl.newMemberOverload(
                "list_flatten",
                "Flattens a list by a single level",
                ListType.create(LIST_PARAM_TYPE),
                ListType.create(ListType.create(LIST_PARAM_TYPE))),
            CelOverloadDecl.newMemberOverload(
                "list_flatten_list_int",
                "Flattens a list to the specified level. A negative depth value flattens the list"
                    + " recursively to its deepest level.",
                ListType.create(SimpleType.DYN),
                ListType.create(SimpleType.DYN),
                SimpleType.INT)),
        CelFunctionBinding.from("list_flatten", Collection.class, list -> flatten(list, 1)),
        CelFunctionBinding.from(
            "list_flatten_list_int", Collection.class, Long.class, CelListsExtensions::flatten)),
    RANGE(
        CelFunctionDecl.newFunctionDeclaration(
            "lists.range",
            CelOverloadDecl.newGlobalOverload(
                "lists_range",
                "Returns a list of integers from 0 to n-1.",
                ListType.create(SimpleType.INT),
                SimpleType.INT)),
        CelFunctionBinding.from("lists_range", Long.class, CelListsExtensions::genRange));

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelFunctionBinding> functionBindings;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, CelFunctionBinding... functionBindings) {
      this.functionDecl = functionDecl;
      this.functionBindings = ImmutableSet.copyOf(functionBindings);
    }
  }

  private final ImmutableSet<Function> functions;

  CelListsExtensions() {
    this.functions = ImmutableSet.copyOf(Function.values());
  }

  CelListsExtensions(Set<Function> functions) {
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

  @SuppressWarnings("unchecked")
  private static ImmutableList<Object> flatten(Collection<Object> list, long depth) {
    Preconditions.checkArgument(depth >= 0, "Level must be non-negative");
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    for (Object element : list) {
      if (!(element instanceof Collection) || depth == 0) {
        builder.add(element);
      } else {
        Collection<Object> listItem = (Collection<Object>) element;
        builder.addAll(flatten(listItem, depth - 1));
      }
    }

    return builder.build();
  }

  public static ImmutableList<Long> genRange(long end) {
    ImmutableList.Builder<Long> builder = ImmutableList.builder();
    for (long i = 0; i < end; i++) {
      builder.add(i);
    }
    return builder.build();
  }
}
