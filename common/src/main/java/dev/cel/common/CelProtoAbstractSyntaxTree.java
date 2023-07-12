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

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.Expr;
import dev.cel.expr.ParsedExpr;
import dev.cel.expr.SourceInfo;
import dev.cel.expr.Type;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.ast.CelExprConverter;
import dev.cel.common.types.CelTypes;
import java.util.Map.Entry;

/**
 * An Adapter for {@link CelAbstractSyntaxTree} constructed from Canonical Protos of {@link
 * CheckedExpr} or {@link ParsedExpr} expressions.
 *
 * <p>Note: Keep this file in sync with {@link CelProtoV1Alpha1AbstractSyntaxTree}
 */
// LINT.IfChange
public final class CelProtoAbstractSyntaxTree {
  private final CheckedExpr checkedExpr;
  private final CelAbstractSyntaxTree ast;

  private CelProtoAbstractSyntaxTree(CheckedExpr checkedExpr) {
    this.checkedExpr = checkedExpr;
    this.ast =
        new CelAbstractSyntaxTree(
            CelExprConverter.fromExpr(checkedExpr.getExpr()),
            CelSource.newBuilder()
                .addAllLineOffsets(checkedExpr.getSourceInfo().getLineOffsetsList())
                .addPositionsMap(checkedExpr.getSourceInfo().getPositionsMap())
                .addAllMacroCalls(
                    CelExprConverter.exprMacroCallsToCelExprMacroCalls(
                        checkedExpr.getSourceInfo().getMacroCallsMap()))
                .setDescription(checkedExpr.getSourceInfo().getLocation())
                .build(),
            checkedExpr.getReferenceMapMap().entrySet().stream()
                .collect(
                    toImmutableMap(
                        Entry::getKey,
                        v -> CelExprConverter.exprReferenceToCelReference(v.getValue()))),
            checkedExpr.getTypeMapMap().entrySet().stream()
                .collect(toImmutableMap(Entry::getKey, v -> CelTypes.typeToCelType(v.getValue()))));
  }

  private CelProtoAbstractSyntaxTree(CelAbstractSyntaxTree ast) {
    this.ast = ast;
    // TODO: The logic of converting a native CEL AST to Checked expression should be
    // moved from CelAbstractSyntaxTree's constructor to here.
    if (ast.isChecked()) {
      this.checkedExpr = ast.toCheckedExpr();
    } else {
      ParsedExpr parsedExpr = ast.toParsedExpr();
      this.checkedExpr =
          CheckedExpr.newBuilder()
              .setExpr(parsedExpr.getExpr())
              .setSourceInfo(parsedExpr.getSourceInfo())
              .build();
    }
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.CheckedExpr}. */
  public static CelProtoAbstractSyntaxTree fromCheckedExpr(CheckedExpr checkedExpr) {
    return new CelProtoAbstractSyntaxTree(checkedExpr);
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.ParsedExpr}. */
  public static CelProtoAbstractSyntaxTree fromParsedExpr(ParsedExpr parsedExpr) {
    return new CelProtoAbstractSyntaxTree(
        CheckedExpr.newBuilder()
            .setExpr(parsedExpr.getExpr())
            .setSourceInfo(parsedExpr.getSourceInfo())
            .build());
  }

  /** Constructs CelProtoAbstractSyntaxTree from {@link CelAbstractSyntaxTree}. */
  public static CelProtoAbstractSyntaxTree fromCelAst(CelAbstractSyntaxTree ast) {
    return new CelProtoAbstractSyntaxTree(ast);
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

  /**
   * Returns the underlying {@link com.google.api.expr.Expr} representation of the abstract syntax
   * tree.
   */
  @CheckReturnValue
  public Expr getExpr() {
    return checkedExpr.getExpr();
  }

  /**
   * Returns the underlying {@link com.google.api.expr.CheckedExpr} representation of the abstract
   * syntax tree. Throws {@link java.lang.IllegalStateException} if {@link
   * CelAbstractSyntaxTree#isChecked} is false.
   */
  @CheckReturnValue
  public CheckedExpr toCheckedExpr() {
    checkState(
        isChecked(),
        "CelAbstractSyntaxTree must be checked before it can be converted to CheckedExpr");
    return checkedExpr;
  }

  /**
   * Returns the underlying {@link com.google.api.expr.SourceInfo} representation of the abstract
   * syntax tree.
   */
  @CheckReturnValue
  public SourceInfo getSourceInfo() {
    return checkedExpr.getSourceInfo();
  }

  /**
   * Returns the underlying {@link com.google.api.expr.ParsedExpr} representation of the abstract
   * syntax tree.
   */
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
    return CelTypes.celTypeToType(ast.getResultType());
  }
}
// LINT.ThenChange(CelProtoV1Alpha1AbstractSyntaxTree.java)
