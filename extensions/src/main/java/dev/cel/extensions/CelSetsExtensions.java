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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import dev.cel.runtime.ProtoMessageRuntimeEquality;
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
public final class CelSetsExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  private static final String SET_CONTAINS_OVERLOAD_DOC =
      "Returns whether the first list argument contains all elements in the second list"
          + " argument. The list may contain elements of any type and standard CEL"
          + " equality is used to determine whether a value exists in both lists. If the"
          + " second list is empty, the result will always return true.";
  private static final String SET_EQUIVALENT_OVERLOAD_DOC =
      "Returns whether the first and second list are set equivalent. Lists are set equivalent if"
          + " for every item in the first list, there is an element in the second which is equal."
          + " The lists may not be of the same size as they do not guarantee the elements within"
          + " them are unique, so size does not factor into the computation.";
  private static final String SET_INTERSECTS_OVERLOAD_DOC =
      "Returns whether the first and second list intersect. Lists intersect if there is at least"
          + " one element in the first list which is equal to an element in the second list. The"
          + " lists may not be of the same size as they do not guarantee the elements within them"
          + " are unique, so size does not factor into the computation. If either list is empty,"
          + " the result will be false.";

  private static final ImmutableMap<SetsFunction, CelFunctionDecl> FUNCTION_DECL_MAP =
      ImmutableMap.of(
          SetsFunction.CONTAINS,
          CelFunctionDecl.newFunctionDeclaration(
              SetsFunction.CONTAINS.getFunction(),
              CelOverloadDecl.newGlobalOverload(
                  "list_sets_contains_list",
                  SET_CONTAINS_OVERLOAD_DOC,
                  SimpleType.BOOL,
                  ListType.create(TypeParamType.create("T")),
                  ListType.create(TypeParamType.create("T")))),
          SetsFunction.EQUIVALENT,
          CelFunctionDecl.newFunctionDeclaration(
              SetsFunction.EQUIVALENT.getFunction(),
              CelOverloadDecl.newGlobalOverload(
                  "list_sets_equivalent_list",
                  SET_EQUIVALENT_OVERLOAD_DOC,
                  SimpleType.BOOL,
                  ListType.create(TypeParamType.create("T")),
                  ListType.create(TypeParamType.create("T")))),
          SetsFunction.INTERSECTS,
          CelFunctionDecl.newFunctionDeclaration(
              SetsFunction.INTERSECTS.getFunction(),
              CelOverloadDecl.newGlobalOverload(
                  "list_sets_intersects_list",
                  SET_INTERSECTS_OVERLOAD_DOC,
                  SimpleType.BOOL,
                  ListType.create(TypeParamType.create("T")),
                  ListType.create(TypeParamType.create("T")))));

  private static final class Library implements CelExtensionLibrary<CelSetsExtensions> {
    private final CelSetsExtensions version0;

    Library(CelOptions celOptions) {
      version0 = new CelSetsExtensions(celOptions);
    }

    @Override
    public String name() {
      return "sets";
    }

    @Override
    public ImmutableSet<CelSetsExtensions> versions() {
      return ImmutableSet.of(version0);
    }
  }

  static CelExtensionLibrary<CelSetsExtensions> library(CelOptions options) {
    return new Library(options);
  }

  private final ImmutableSet<SetsFunction> functions;
  private final SetsExtensionsRuntimeImpl setsExtensionsRuntime;

  CelSetsExtensions(CelOptions celOptions) {
    this(celOptions, ImmutableSet.copyOf(SetsFunction.values()));
  }

  CelSetsExtensions(CelOptions celOptions, Set<SetsFunction> functions) {
    this.functions = ImmutableSet.copyOf(functions);
    ProtoMessageRuntimeEquality runtimeEquality =
        ProtoMessageRuntimeEquality.create(
            DynamicProto.create(DefaultMessageFactory.INSTANCE), celOptions);
    this.setsExtensionsRuntime = new SetsExtensionsRuntimeImpl(runtimeEquality, functions);
  }

  @Override
  public int version() {
    return 0;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return ImmutableSet.copyOf(FUNCTION_DECL_MAP.values());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(
        function -> checkerBuilder.addFunctionDeclarations(FUNCTION_DECL_MAP.get(function)));
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(setsExtensionsRuntime.newFunctionBindings());
  }
}
