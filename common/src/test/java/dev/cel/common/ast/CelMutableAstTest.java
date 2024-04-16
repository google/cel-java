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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelMutableAstTest {

  @Test
  public void constructMutableAst() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofConstant(1L, CelConstant.ofValue("hello world"));
    CelSource.Builder sourceBuilder = CelSource.newBuilder();

    CelMutableAst celMutableAst = CelMutableAst.of(mutableExpr, sourceBuilder);

    assertThat(celMutableAst.expr()).isEqualTo(mutableExpr);
    assertThat(celMutableAst.source()).isSameInstanceAs(sourceBuilder);
  }

  @Test
  public void fromCelAst_mutableAst_containsMutableExpr() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("'hello world'").getAst();

    CelMutableAst celMutableAst = CelMutableAst.fromCelAst(ast);

    assertThat(celMutableAst.expr())
        .isEqualTo(CelMutableExpr.ofConstant(1L, CelConstant.ofValue("hello world")));
  }

  @Test
  public void getType_success() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("'hello world'").getAst();
    CelMutableAst celMutableAst = CelMutableAst.fromCelAst(ast);

    assertThat(celMutableAst.getType(1L)).hasValue(SimpleType.STRING);
  }

  @Test
  public void getReference_success() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("size('test')").getAst();
    CelMutableAst celMutableAst = CelMutableAst.fromCelAst(ast);

    assertThat(celMutableAst.getReference(1L))
        .hasValue(CelReference.newBuilder().addOverloadIds("size_string").build());
  }

  @Test
  public void parsedAst_roundTrip() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(CelOptions.current().populateMacroCalls(true).build())
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .build();
    CelAbstractSyntaxTree ast = celCompiler.parse("[1].exists(x, x > 0)").getAst();
    CelMutableAst celMutableAst = CelMutableAst.fromCelAst(ast);

    CelAbstractSyntaxTree roundTrippedAst = celMutableAst.toParsedAst();

    assertThat(ast.getExpr()).isEqualTo(roundTrippedAst.getExpr());
    assertThat(roundTrippedAst.getSource().getMacroCalls()).hasSize(1);
    assertThat(roundTrippedAst.getSource().getMacroCalls())
        .containsExactlyEntriesIn(ast.getSource().getMacroCalls());
  }
}
