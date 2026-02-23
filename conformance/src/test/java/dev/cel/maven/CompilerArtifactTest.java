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

package dev.cel.maven;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.checker.CelChecker;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.ProtoMessageTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.parser.CelParser;
import dev.cel.parser.CelParserFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CompilerArtifactTest {

  @Test
  public void parse() throws Exception {
    CelParser parser = CelParserFactory.standardCelParserBuilder().build();
    CelUnparser unparser = CelUnparserFactory.newUnparser();

    CelAbstractSyntaxTree ast = parser.parse("'Hello World'").getAst();

    assertThat(ast.getExpr()).isEqualTo(CelExpr.ofConstant(1L, CelConstant.ofValue("Hello World")));
    assertThat(unparser.unparse(ast)).isEqualTo("\"Hello World\"");
  }

  @Test
  public void typeCheck() throws Exception {
    CelParser parser = CelParserFactory.standardCelParserBuilder().build();
    CelChecker checker = CelCompilerFactory.standardCelCheckerBuilder().build();

    CelAbstractSyntaxTree ast = checker.check(parser.parse("'Hello World'").getAst()).getAst();

    assertThat(ast.getResultType()).isEqualTo(SimpleType.STRING);
  }

  @Test
  public void compile() throws Exception {
    CelCompiler compiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "getThree", newGlobalOverload("getThree_overload", SimpleType.INT)))
            .setOptions(CelOptions.DEFAULT)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .setTypeProvider(
                ProtoMessageTypeProvider.newBuilder()
                    .addFileDescriptors(TestAllTypes.getDescriptor().getFile())
                    .build())
            .setContainer(CelContainer.ofName("cel.expr.conformance.proto3"))
            .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .build();
    CelUnparser unparser = CelUnparserFactory.newUnparser();

    CelAbstractSyntaxTree ast =
        compiler.compile("msg == TestAllTypes{} && 3 == getThree()").getAst();

    assertThat(unparser.unparse(ast))
        .isEqualTo("msg == cel.expr.conformance.proto3.TestAllTypes{} && 3 == getThree()");
    assertThat(ast.getResultType()).isEqualTo(SimpleType.BOOL);
  }

  @Test
  public void compile_error() {
    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();

    CelValidationResult result = compiler.compile("'foo' + 1");

    assertThat(result.hasError()).isTrue();
    assertThat(assertThrows(CelValidationException.class, result::getAst))
        .hasMessageThat()
        .contains("found no matching overload for '_+_' applied to '(string, int)'");
  }
}
