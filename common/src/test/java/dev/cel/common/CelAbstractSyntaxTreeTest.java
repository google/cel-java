// Copyright 2022 Google LLC
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

package dev.cel.common;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.Expr.Ident;
import dev.cel.expr.Reference;
import dev.cel.expr.SourceInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelAbstractSyntaxTreeTest {

  private static final CelExpr EXPR =
      CelExpr.newBuilder().setId(1L).setConstant(CelConstant.ofValue(true)).build();
  private static final CelSource SOURCE_INFO =
      CelSource.newBuilder()
          .setDescription("test/location.cel")
          .addPositions(1L, 0)
          .addLineOffsets(4)
          .build();
  private static final CelAbstractSyntaxTree PARSED_AST =
      CelAbstractSyntaxTree.newParsedAst(EXPR, SOURCE_INFO);
  private static final CelAbstractSyntaxTree CHECKED_AST =
      CelAbstractSyntaxTree.newCheckedAst(
          EXPR, SOURCE_INFO, ImmutableMap.of(), ImmutableMap.of(1L, SimpleType.BOOL));

  private static final CelAbstractSyntaxTree CHECKED_ENUM_AST =
      CelProtoAbstractSyntaxTree.fromCheckedExpr(
              CheckedExpr.newBuilder()
                  .putTypeMap(1L, CelProtoTypes.INT64)
                  .putTypeMap(2L, CelProtoTypes.INT64)
                  .putTypeMap(3L, CelProtoTypes.BOOL)
                  .putReferenceMap(
                      2L,
                      Reference.newBuilder()
                          .setValue(Constant.newBuilder().setInt64Value(2))
                          .build())
                  .putReferenceMap(3L, Reference.newBuilder().addOverloadId("not_equals").build())
                  .setExpr(
                      Expr.newBuilder()
                          .setId(3L)
                          .setCallExpr(
                              Call.newBuilder()
                                  .setFunction("_!=_")
                                  .addArgs(
                                      Expr.newBuilder()
                                          .setId(1L)
                                          .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                                  .addArgs(
                                      Expr.newBuilder()
                                          .setId(2L)
                                          .setIdentExpr(Ident.newBuilder().setName("ENUM")))))
                  .setSourceInfo(
                      SourceInfo.newBuilder()
                          .setLocation("test/location.cel")
                          .putPositions(1L, 0)
                          .putPositions(2L, 4)
                          .putPositions(3L, 2)
                          .addLineOffsets(8))
                  .build())
          .getAst();

  @Test
  public void findEnumValue_findsConstantInCheckedExpr() {
    CelAbstractSyntaxTree ast = CHECKED_ENUM_AST;
    assertThat(ast.findEnumValue(1)).isEmpty();
    assertThat(ast.findEnumValue(2)).hasValue(CelConstant.ofValue(2));
    assertThat(ast.findEnumValue(3)).isEmpty();
  }

  @Test
  public void findEnumValue_doesNotFindConstantInParsedExpr() {
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.newParsedAst(
            CHECKED_ENUM_AST.getExpr(), CHECKED_ENUM_AST.getSource());

    assertThat(ast.findEnumValue(1)).isEmpty();
    assertThat(ast.findEnumValue(2)).isEmpty();
    assertThat(ast.findEnumValue(3)).isEmpty();
  }

  @Test
  public void findOverloadIDs_findsOverloadsInCheckedExpr() {
    CelAbstractSyntaxTree ast = CHECKED_ENUM_AST;
    assertThat(ast.findOverloadIDs(1)).isEmpty();
    assertThat(ast.findOverloadIDs(2)).isEmpty();
    assertThat(ast.findOverloadIDs(3)).hasValue(ImmutableList.of("not_equals"));
  }

  @Test
  public void findOverloadIDs_doesNotFindsOverloadsInParsedExpr() {
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.newParsedAst(
            CHECKED_ENUM_AST.getExpr(), CHECKED_ENUM_AST.getSource());

    assertThat(ast.findOverloadIDs(1)).isEmpty();
    assertThat(ast.findOverloadIDs(2)).isEmpty();
    assertThat(ast.findOverloadIDs(3)).isEmpty();
  }

  @Test
  public void getSource_hasDescriptionEqualToSourceLocation() {
    assertThat(PARSED_AST.getSource().getDescription()).isEqualTo("test/location.cel");
  }

  @Test
  public void equalityTest() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .setOptions(CelOptions.current().populateMacroCalls(true).build())
            .build();
    new EqualsTester()
        .addEqualityGroup(
            CelAbstractSyntaxTree.newParsedAst(
                CelExpr.newBuilder().build(), CelSource.newBuilder().build()))
        .addEqualityGroup(
            celCompiler.compile("'foo'").getAst(), celCompiler.compile("'foo'").getAst()) // ASCII
        .addEqualityGroup(
            celCompiler.compile("'가나다'").getAst(), celCompiler.compile("'가나다'").getAst()) // BMP
        .addEqualityGroup(
            celCompiler.compile("'😦😁😑'").getAst(),
            celCompiler.compile("'😦😁😑'").getAst()) // SMP
        .addEqualityGroup(
            celCompiler.compile("[1,2,3].exists(x, x > 0)").getAst(),
            celCompiler.compile("[1,2,3].exists(x, x > 0)").getAst())
        .testEquals();
  }

  @Test
  public void parsedExpression_createAst() {
    CelExpr celExpr = CelExpr.newBuilder().setId(1).setConstant(CelConstant.ofValue(2)).build();
    CelSource celSource =
        CelSource.newBuilder("expression").setDescription("desc").addPositions(1, 5).build();

    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.newParsedAst(celExpr, celSource);

    assertThat(ast).isNotNull();
    assertThat(ast.getExpr()).isEqualTo(celExpr);
    assertThat(ast.getSource()).isEqualTo(celSource);
    assertThat(ast.getTypeMap()).isEmpty();
    assertThat(ast.getReferenceMap()).isEmpty();
  }
}
