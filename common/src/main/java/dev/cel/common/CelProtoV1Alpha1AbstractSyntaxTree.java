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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.ast.CelExprV1Alpha1Converter;
import dev.cel.common.types.CelV1AlphaTypes;
import java.util.Map.Entry;

/**
 * An Adapter for {@link CelAbstractSyntaxTree} constructed from v1alpha1 Protos of {@link
 * CheckedExpr} or {@link ParsedExpr} expressions.
 *
 * <p>Note: Clients should prefer using {@link CelProtoAbstractSyntaxTree} instead. This only exists
 * to maintain compatibility with the deprecated v1alpha1 protos.
 *
 * <p>Note: Keep this file in sync with {@link CelProtoAbstractSyntaxTree}
 */
// LINT.IfChange
public final class CelProtoV1Alpha1AbstractSyntaxTree {
  private final CheckedExpr checkedExpr;
  private final CelAbstractSyntaxTree ast;

  private CelProtoV1Alpha1AbstractSyntaxTree(CheckedExpr checkedExpr) {
    this.checkedExpr = checkedExpr;
    this.ast =
        new CelAbstractSyntaxTree(
            CelExprV1Alpha1Converter.fromExpr(checkedExpr.getExpr()),
            CelSource.newBuilder()
                .addAllLineOffsets(checkedExpr.getSourceInfo().getLineOffsetsList())
                .addPositionsMap(checkedExpr.getSourceInfo().getPositionsMap())
                .setDescription(checkedExpr.getSourceInfo().getLocation())
                .build(),
            checkedExpr.getReferenceMapMap().entrySet().stream()
                .collect(
                    toImmutableMap(
                        Entry::getKey,
                        v -> CelExprV1Alpha1Converter.exprReferenceToCelReference(v.getValue()))),
            checkedExpr.getTypeMapMap().entrySet().stream()
                .collect(
                    toImmutableMap(
                        Entry::getKey, v -> CelV1AlphaTypes.typeToCelType(v.getValue()))));
  }

  private CelProtoV1Alpha1AbstractSyntaxTree(CelAbstractSyntaxTree ast) {
    this.ast = ast;

    CheckedExpr.Builder checkedExprBuilder =
        CheckedExpr.newBuilder()
            .setSourceInfo(
                SourceInfo.newBuilder()
                    .setLocation(ast.getSource().getDescription())
                    .addAllLineOffsets(ast.getSource().getLineOffsets().asList())
                    .putAllPositions(ast.getSource().getPositionsMap()))
            .setExpr(CelExprV1Alpha1Converter.fromCelExpr(ast.getExpr()));

    if (ast.isChecked()) {
      checkedExprBuilder.putAllReferenceMap(
          ast.getReferenceMap().entrySet().stream()
              .collect(
                  toImmutableMap(
                      Entry::getKey,
                      v -> CelExprV1Alpha1Converter.celReferenceToExprReference(v.getValue()))));
      checkedExprBuilder.putAllTypeMap(
          ast.getTypeMap().entrySet().stream()
              .collect(
                  toImmutableMap(Entry::getKey, v -> CelV1AlphaTypes.celTypeToType(v.getValue()))));
    }

    this.checkedExpr = checkedExprBuilder.build();
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.v1alpha1.CheckedExpr}. */
  public static CelProtoV1Alpha1AbstractSyntaxTree fromCheckedExpr(CheckedExpr checkedExpr) {
    return new CelProtoV1Alpha1AbstractSyntaxTree(checkedExpr);
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.v1alpha1.ParsedExpr}. */
  public static CelProtoV1Alpha1AbstractSyntaxTree fromParsedExpr(ParsedExpr parsedExpr) {
    return new CelProtoV1Alpha1AbstractSyntaxTree(
        CheckedExpr.newBuilder()
            .setExpr(parsedExpr.getExpr())
            .setSourceInfo(parsedExpr.getSourceInfo())
            .build());
  }

  /** Constructs CelProtoAbstractSyntaxTree from {@link CelAbstractSyntaxTree}. */
  public static CelProtoV1Alpha1AbstractSyntaxTree fromCelAst(CelAbstractSyntaxTree ast) {
    return new CelProtoV1Alpha1AbstractSyntaxTree(ast);
  }

  /** Tests whether the underlying abstract syntax tree has been type checked or not. */
  public boolean isChecked() {
    return ast.isChecked();
  }

  /**
   * Returns the native representation of the abstract syntax tree: {@link CelAbstractSyntaxTree}.
   */
  @CheckReturnValue
  public CelAbstractSyntaxTree getAst() {
    return ast;
  }

  /** Returns the underlying {@link Expr} representation of the abstract syntax tree. */
  @CheckReturnValue
  public Expr getExpr() {
    return checkedExpr.getExpr();
  }

  /**
   * Returns the underlying {@link CheckedExpr} representation of the abstract syntax tree. Throws
   * {@link IllegalStateException} if {@link CelAbstractSyntaxTree#isChecked} is false.
   */
  @CheckReturnValue
  public CheckedExpr toCheckedExpr() {
    checkState(
        isChecked(),
        "CelAbstractSyntaxTree must be checked before it can be converted to CheckedExpr");
    return checkedExpr;
  }

  /** Returns the underlying {@link SourceInfo} representation of the abstract syntax tree. */
  @CheckReturnValue
  public SourceInfo getSourceInfo() {
    return checkedExpr.getSourceInfo();
  }

  /** Returns the underlying {@link ParsedExpr} representation of the abstract syntax tree. */
  @CheckReturnValue
  public ParsedExpr toParsedExpr() {
    return ParsedExpr.newBuilder().setExpr(getExpr()).setSourceInfo(getSourceInfo()).build();
  }

  /**
   * For a type checked abstract syntax tree the resulting type is returned in proto format
   * described in checked.proto. Otherwise, the dynamic type is returned.
   */
  @CheckReturnValue
  public Type getProtoResultType() {
    return CelV1AlphaTypes.celTypeToType(ast.getResultType());
  }
}
// LINT.ThenChange(CelProtoAbstractSyntaxTree.java)
