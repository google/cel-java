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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.ParsedExpr;
import dev.cel.expr.Reference;
import dev.cel.expr.SourceInfo;
import dev.cel.expr.SourceInfo.Extension;
import dev.cel.expr.SourceInfo.Extension.Component;
import dev.cel.expr.SourceInfo.Extension.Version;
import dev.cel.common.types.CelProtoTypes;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// LINT.IfChange
@RunWith(JUnit4.class)
public class CelProtoAbstractSyntaxTreeTest {
  private static final Expr EXPR =
      Expr.newBuilder()
          .setId(1L)
          .setCallExpr(
              Call.newBuilder()
                  .setFunction("_!=_")
                  .addAllArgs(
                      Arrays.asList(
                          Expr.newBuilder()
                              .setId(2)
                              .setConstExpr(Constant.newBuilder().setBoolValue(true).build())
                              .build(),
                          Expr.newBuilder()
                              .setId(3)
                              .setConstExpr(Constant.newBuilder().setBoolValue(false).build())
                              .build()))
                  .build())
          .build();
  private static final SourceInfo SOURCE_INFO =
      SourceInfo.newBuilder()
          .setLocation("test/location.cel")
          .putPositions(1L, 0)
          .addLineOffsets(4)
          .addExtensions(
              Extension.newBuilder()
                  .setId("extension_id")
                  .addAffectedComponents(Component.COMPONENT_PARSER)
                  .addAffectedComponents(Component.COMPONENT_TYPE_CHECKER)
                  .addAffectedComponents(Component.COMPONENT_RUNTIME)
                  .setVersion(Version.newBuilder().setMajor(5).setMinor(3)))
          .putMacroCalls(
              2,
              Expr.newBuilder()
                  .setId(4)
                  .setConstExpr(Constant.newBuilder().setStringValue("Hello"))
                  .build())
          .build();
  private static final ParsedExpr PARSED_EXPR =
      ParsedExpr.newBuilder().setExpr(EXPR).setSourceInfo(SOURCE_INFO).build();

  private static final CheckedExpr CHECKED_EXPR =
      CheckedExpr.newBuilder()
          .putTypeMap(1L, CelProtoTypes.BOOL)
          .putReferenceMap(1L, Reference.newBuilder().addOverloadId("not_equals").build())
          .setSourceInfo(SOURCE_INFO)
          .setExpr(EXPR)
          .build();

  @Test
  public void toParsedExpr_yieldsEquivalentMessage() {
    CelProtoAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.toParsedExpr()).isEqualTo(PARSED_EXPR);
  }

  @Test
  public void toCheckedExpr_throwsWhenParsedExpr() {
    CelProtoAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getAst().isChecked()).isFalse();
    assertThrows(IllegalStateException.class, ast::toCheckedExpr);
  }

  @Test
  public void toCheckedExpr_yieldsEquivalentMessage() {
    CelProtoAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR);
    assertThat(ast.getAst().isChecked()).isTrue();
    assertThat(ast.toCheckedExpr()).isEqualTo(CHECKED_EXPR);
  }

  @Test
  public void getSourceInfo_yieldsEquivalentMessage() {
    CelProtoAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getSourceInfo()).isEqualTo(SOURCE_INFO);
  }

  @Test
  public void getProtoResultType_isDynWhenParsedExpr() {
    CelProtoAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getProtoResultType()).isEqualTo(CelProtoTypes.DYN);
  }

  @Test
  public void getProtoResultType_isStaticWhenCheckedExpr() {
    CelProtoAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR);
    assertThat(ast.getProtoResultType()).isEqualTo(CelProtoTypes.BOOL);
  }

  @Test
  public void fromCelAst_toParsedExpr_roundTrip() {
    CelAbstractSyntaxTree celAst = CelProtoAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR).getAst();

    assertThat(CelProtoAbstractSyntaxTree.fromCelAst(celAst).toParsedExpr()).isEqualTo(PARSED_EXPR);
  }

  @Test
  public void fromCelAst_toCheckedExpr_roundTrip() {
    CelAbstractSyntaxTree celAst =
        CelProtoAbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR).getAst();

    assertThat(CelProtoAbstractSyntaxTree.fromCelAst(celAst).toCheckedExpr())
        .isEqualTo(CHECKED_EXPR);
  }
}
// LINT.ThenChange(CelProtoV1Alpha1AbstractSyntaxTreeTest.java)
