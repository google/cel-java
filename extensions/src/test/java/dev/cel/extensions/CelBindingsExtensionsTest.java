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

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelBindingsExtensionsTest {

  private static final CelCompiler COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addLibraries(CelExtensions.bindings())
          .build();

  private static final CelRuntime RUNTIME = CelRuntimeFactory.standardCelRuntimeBuilder().build();

  @Test
  @TestParameters("{expr: 'cel.bind(t, true, t)', expectedResult: true}")
  @TestParameters(
      "{expr: 'cel.bind(msg, \"hello\", msg + msg + msg) == \"hellohellohello\"',"
          + " expectedResult: true}")
  @TestParameters(
      "{expr: 'cel.bind(t1, true, cel.bind(t2, true, t1 && t2))', expectedResult: true}")
  @TestParameters(
      "{expr: 'cel.bind(valid_elems, [1, 2, 3], [3, 4, 5]"
          + ".exists(e, e in valid_elems))', expectedResult: true}")
  @TestParameters(
      "{expr: 'cel.bind(valid_elems, [1, 2, 3], ![4, 5].exists(e, e in valid_elems))',"
          + " expectedResult: true}")
  public void binding_success(String expr, boolean expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);
    Object evaluatedResult = program.eval();

    assertThat(evaluatedResult).isEqualTo(expectedResult);
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
}
