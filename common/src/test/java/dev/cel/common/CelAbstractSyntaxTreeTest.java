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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.Expr.Ident;
import dev.cel.expr.ParsedExpr;
import dev.cel.expr.Reference;
import dev.cel.expr.SourceInfo;
import com.google.common.collect.ImmutableList;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.SimpleType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelAbstractSyntaxTreeTest {

  private static final Expr EXPR =
      Expr.newBuilder()
          .setId(1L)
          .setConstExpr(Constant.newBuilder().setBoolValue(true).build())
          .build();
  private static final SourceInfo SOURCE_INFO =
      SourceInfo.newBuilder()
          .setLocation("test/location.cel")
          .putPositions(1L, 0)
          .addLineOffsets(4)
          .build();
  private static final ParsedExpr PARSED_EXPR =
      ParsedExpr.newBuilder().setExpr(EXPR).setSourceInfo(SOURCE_INFO).build();
  private static final CheckedExpr CHECKED_EXPR =
      CheckedExpr.newBuilder()
          .putTypeMap(1L, CelTypes.BOOL)
          .setSourceInfo(SOURCE_INFO)
          .setExpr(EXPR)
          .build();

  private static final CheckedExpr CHECKED_ENUM_EXPR =
      CheckedExpr.newBuilder()
          .putTypeMap(1L, CelTypes.INT64)
          .putTypeMap(2L, CelTypes.INT64)
          .putTypeMap(3L, CelTypes.BOOL)
          .putReferenceMap(
              2L, Reference.newBuilder().setValue(Constant.newBuilder().setInt64Value(2)).build())
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
          .build();

  @Test
  public void toParsedExpr_yieldsEquivalentMessage() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.toParsedExpr()).isEqualTo(PARSED_EXPR);
  }

  @Test
  public void toCheckedExpr_throwsWhenParsedExpr() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.isChecked()).isFalse();
    assertThrows(IllegalStateException.class, ast::toCheckedExpr);
  }

  @Test
  public void toCheckedExpr_yieldsEquivalentMessage() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR);
    assertThat(ast.isChecked()).isTrue();
    assertThat(ast.toCheckedExpr()).isEqualTo(CHECKED_EXPR);
  }

  @Test
  public void getResultType_isDynWhenParsedExpr() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);

    assertThat(ast.getProtoResultType()).isEqualTo(CelTypes.DYN);
    assertThat(ast.getResultType()).isEqualTo(SimpleType.DYN);
  }

  @Test
  public void getResultType_isStaticWhenCheckedExpr() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromCheckedExpr(CHECKED_EXPR);

    assertThat(ast.getProtoResultType()).isEqualTo(CelTypes.BOOL);
    assertThat(ast.getResultType()).isEqualTo(SimpleType.BOOL);
  }

  @Test
  public void getExpr_yieldsEquivalentMessage() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getProtoExpr()).isEqualTo(EXPR);
  }

  @Test
  public void findEnumValue_findsConstantInCheckedExpr() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromCheckedExpr(CHECKED_ENUM_EXPR);
    assertThat(ast.findEnumValue(1)).isEmpty();
    assertThat(ast.findEnumValue(2)).hasValue(CelConstant.ofValue(2));
    assertThat(ast.findEnumValue(3)).isEmpty();
  }

  @Test
  public void findEnumValue_doesNotFindConstantInParsedExpr() {
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.fromParsedExpr(
            ParsedExpr.newBuilder()
                .setExpr(CHECKED_ENUM_EXPR.getExpr())
                .setSourceInfo(CHECKED_ENUM_EXPR.getSourceInfo())
                .build());
    assertThat(ast.findEnumValue(1)).isEmpty();
    assertThat(ast.findEnumValue(2)).isEmpty();
    assertThat(ast.findEnumValue(3)).isEmpty();
  }

  @Test
  public void findOverloadIDs_findsOverloadsInCheckedExpr() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromCheckedExpr(CHECKED_ENUM_EXPR);
    assertThat(ast.findOverloadIDs(1)).isEmpty();
    assertThat(ast.findOverloadIDs(2)).isEmpty();
    assertThat(ast.findOverloadIDs(3)).hasValue(ImmutableList.of("not_equals"));
  }

  @Test
  public void findOverloadIDs_doesNotFindsOverloadsInParsedExpr() {
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.fromParsedExpr(
            ParsedExpr.newBuilder()
                .setExpr(CHECKED_ENUM_EXPR.getExpr())
                .setSourceInfo(CHECKED_ENUM_EXPR.getSourceInfo())
                .build());
    assertThat(ast.findOverloadIDs(1)).isEmpty();
    assertThat(ast.findOverloadIDs(2)).isEmpty();
    assertThat(ast.findOverloadIDs(3)).isEmpty();
  }

  @Test
  public void getSource_hasDescriptionEqualToSourceLocation() {
    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.fromParsedExpr(PARSED_EXPR);
    assertThat(ast.getSource().getDescription()).isEqualTo("test/location.cel");
  }
}
