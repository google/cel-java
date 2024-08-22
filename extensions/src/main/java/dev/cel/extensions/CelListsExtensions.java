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
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
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
                ListType.create(ListType.create(LIST_PARAM_TYPE)))),
        // TODO: add list_flatten_list_int
        CelRuntime.CelFunctionBinding.from(
            "list_flatten", Collection.class, list -> flatten(list, 1))),
    ;

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelFunctionBinding> functionBindings;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, CelRuntime.CelFunctionBinding... functionBindings) {
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
  private static ImmutableList<Object> flatten(Collection<Object> list, int level) {
    Preconditions.checkArgument(level == 1, "recursive flatten is not supported yet.");
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    for (Object element : list) {
      if (element instanceof Collection) {
        Collection<Object> listItem = (Collection<Object>) element;
        builder.addAll(listItem);
      } else {
        builder.add(element);
      }
    }

    return builder.build();
  }
}
