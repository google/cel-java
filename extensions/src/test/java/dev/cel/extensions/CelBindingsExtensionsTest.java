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

package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelBindingsExtensionsTest {

  private static final CelCompiler COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addLibraries(CelOptionalLibrary.INSTANCE, CelExtensions.bindings())
          .build();

  private static final CelRuntime RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addLibraries(CelOptionalLibrary.INSTANCE)
          .build();

  private enum BindingTestCase {
    BOOL_LITERAL("cel.bind(t, true, t)"),
    STRING_CONCAT("cel.bind(msg, \"hello\", msg + msg + msg) == \"hellohellohello\""),
    NESTED_BINDS("cel.bind(t1, true, cel.bind(t2, true, t1 && t2))"),
    NESTED_BINDS_SPECIFIER_ONLY(
        "cel.bind(x, cel.bind(x, \"a\", x + x), x + \":\" + x) == \"aa:aa\""),
    NESTED_BINDS_SPECIFIER_AND_VALUE(
        "cel.bind(x, cel.bind(x, \"a\", x + x), cel.bind(y, x + x, y + \":\" + y)) =="
            + " \"aaaa:aaaa\""),
    BIND_WITH_EXISTS_TRUE(
        "cel.bind(valid_elems, [1, 2, 3], [3, 4, 5].exists(e, e in valid_elems))"),
    BIND_WITH_EXISTS_FALSE("cel.bind(valid_elems, [1, 2, 3], ![4, 5].exists(e, e in valid_elems))"),
    BIND_WITH_MAP("[1,2,3].map(x, cel.bind(y, x + x, [y, y])) == [[2, 2], [4, 4], [6, 6]]"),
    BIND_OPTIONAL_LIST("cel.bind(r0, optional.none(), [?r0, ?r0]) == []");

    private final String source;

    BindingTestCase(String source) {
      this.source = source;
    }
  }

  @Test
  public void binding_success(@TestParameter BindingTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(testCase.source).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);
    boolean evaluatedResult = (boolean) program.eval();

    assertThat(evaluatedResult).isTrue();
  }

  @Test
  @TestParameters("{expr: 'false.bind(false, false, false)'}")
  public void binding_nonCelNamespace_success(String expr) throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.bindings())
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "bind",
                    CelOverloadDecl.newMemberOverload(
                        "bool_bind_bool_bool_bool",
                        SimpleType.BOOL,
                        SimpleType.BOOL,
                        SimpleType.BOOL,
                        SimpleType.BOOL,
                        SimpleType.BOOL)))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "bool_bind_bool_bool_bool",
                    Arrays.asList(Boolean.class, Boolean.class, Boolean.class, Boolean.class),
                    (args) -> true))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    boolean result = (boolean) celRuntime.createProgram(ast).eval();
    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expr: 'cel.bind(bad.name, true, bad.name)'}")
  public void binding_throwsCompilationException(String expr) throws Exception {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> COMPILER.compile(expr).getAst());

    assertThat(e).hasMessageThat().contains("cel.bind() variable name must be a simple identifier");
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyBinding_bindingVarNeverReferenced() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setStandardMacros(CelStandardMacro.HAS)
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .addLibraries(CelExtensions.bindings())
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "get_true",
                    CelOverloadDecl.newGlobalOverload("get_true_overload", SimpleType.BOOL)))
            .build();
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        celCompiler.compile("cel.bind(t, get_true(), has(msg.single_int64) ? t : false)").getAst();

    boolean result =
        (boolean)
            celRuntime
                .createProgram(ast)
                .eval(ImmutableMap.of("msg", TestAllTypes.getDefaultInstance()));

    assertThat(result).isFalse();
    assertThat(invocation.get()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyBinding_accuInitEvaluatedOnce() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.bindings())
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "get_true",
                    CelOverloadDecl.newGlobalOverload("get_true_overload", SimpleType.BOOL)))
            .build();
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        celCompiler.compile("cel.bind(t, get_true(), t && t && t && t)").getAst();

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(1);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyBinding_withNestedBinds() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.bindings())
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "get_true",
                    CelOverloadDecl.newGlobalOverload("get_true_overload", SimpleType.BOOL)))
            .build();
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        celCompiler
            .compile("cel.bind(t1, get_true(), cel.bind(t2, get_true(), t1 && t2 && t1 && t2))")
            .getAst();

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(2);
  }
}
