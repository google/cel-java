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

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.SourceInfo;
import dev.cel.common.types.CelV1AlphaTypes;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// LINT.IfChange
@RunWith(JUnit4.class)
public class CelProtoV1Alpha1AbstractSyntaxTreeTest {
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
          .putTypeMap(1L, CelV1AlphaTypes.BOOL)
          .setSourceInfo(SOURCE_INFO)
          .putReferenceMap(1L, Reference.newBuilder().addOverloadId("not_equals").build())
          .setExpr(EXPR)
          .build();

  @Test
  public void toParsedExpr_yieldsEquivalentMessage() {
    CelProtoV1Alpha1AbstractSyntaxTree ast =
        CelProtoV1Alpha1AbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.toParsedExpr()).isEqualTo(PARSED_EXPR);
  }

  @Test
  public void toCheckedExpr_throwsWhenParsedExpr() {
    CelProtoV1Alpha1AbstractSyntaxTree ast =
        CelProtoV1Alpha1AbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getAst().isChecked()).isFalse();
    assertThrows(IllegalStateException.class, ast::toCheckedExpr);
  }

  @Test
  public void toCheckedExpr_yieldsEquivalentMessage() {
    CelProtoV1Alpha1AbstractSyntaxTree ast =
        CelProtoV1Alpha1AbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR);
    assertThat(ast.getAst().isChecked()).isTrue();
    assertThat(ast.toCheckedExpr()).isEqualTo(CHECKED_EXPR);
  }

  @Test
  public void getSourceInfo_yieldsEquivalentMessage() {
    CelProtoV1Alpha1AbstractSyntaxTree ast =
        CelProtoV1Alpha1AbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getSourceInfo()).isEqualTo(SOURCE_INFO);
  }

  @Test
  public void getProtoResultType_isDynWhenParsedExpr() {
    CelProtoV1Alpha1AbstractSyntaxTree ast =
        CelProtoV1Alpha1AbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getProtoResultType()).isEqualTo(CelV1AlphaTypes.DYN);
  }

  @Test
  public void getProtoResultType_isStaticWhenCheckedExpr() {
    CelProtoV1Alpha1AbstractSyntaxTree ast =
        CelProtoV1Alpha1AbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR);
    assertThat(ast.getProtoResultType()).isEqualTo(CelV1AlphaTypes.BOOL);
  }

  @Test
  public void fromCelAst_toParsedExpr_roundTrip() {
    CelAbstractSyntaxTree celAst =
        CelProtoV1Alpha1AbstractSyntaxTree.fromParsedExpr(PARSED_EXPR).getAst();

    assertThat(CelProtoV1Alpha1AbstractSyntaxTree.fromCelAst(celAst).toParsedExpr())
        .isEqualTo(PARSED_EXPR);
  }

  @Test
  public void fromCelAst_toCheckedExpr_roundTrip() {
    CelAbstractSyntaxTree celAst =
        CelProtoV1Alpha1AbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR).getAst();

    assertThat(CelProtoV1Alpha1AbstractSyntaxTree.fromCelAst(celAst).toCheckedExpr())
        .isEqualTo(CHECKED_EXPR);
  }
}
// LINT.ThenChange(CelProtoAbstractSyntaxTreeTest.java)
