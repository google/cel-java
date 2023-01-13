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

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ExprOrBuilder;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a checked or unchecked expression, its source, and related metadata such as source
 * position information.
 */
@Immutable
public final class CelAbstractSyntaxTree {

  private final CheckedExpr checkedExpr;
  private final CelSource source;

  @SuppressWarnings("Immutable") // Protocol Buffer library ensures unmodifiable.
  private final Map<Long, Reference> references;

  @SuppressWarnings("Immutable") // Protocol Buffer library ensures unmodifiable.
  private final Map<Long, Type> types;

  CelAbstractSyntaxTree(ParsedExpr parsedExpr, CelSource source) {
    this(
        CheckedExpr.newBuilder()
            .setExpr(parsedExpr.getExpr())
            .setSourceInfo(parsedExpr.getSourceInfo())
            .build(),
        source);
  }

  CelAbstractSyntaxTree(CheckedExpr checkedExpr, CelSource source) {
    this.checkedExpr = checkedExpr;
    this.source = source;
    this.references = checkedExpr.getReferenceMapMap();
    this.types = checkedExpr.getTypeMapMap();
  }

  /**
   * Returns the underlying {@link com.google.api.expr.Expr} representation of the abstract syntax
   * tree.
   */
  public Expr getExpr() {
    return checkedExpr.getExpr();
  }

  /** Tests whether the underlying abstract syntax tree has been type checked or not. */
  public boolean isChecked() {
    return !types.isEmpty();
  }

  /**
   * For a type checked abstract syntax tree the resulting type is returned. Otherwise, the dynamic
   * type is returned.
   */
  public CelType getResultType() {
    return CelTypes.typeToCelType(getProtoResultType());
  }

  /**
   * For a type checked abstract syntax tree the resulting type is returned in proto format
   * described in checked.proto. Otherwise, the dynamic type is returned.
   */
  public Type getProtoResultType() {
    return isChecked() ? getType(getExpr()) : CelTypes.DYN;
  }

  /**
   * Returns the {@link CelSource} that was used during construction of the abstract syntax tree.
   */
  public CelSource getSource() {
    return source;
  }

  /**
   * Returns the underlying {@link com.google.api.expr.SourceInfo} representation of the abstract
   * syntax tree.
   */
  public SourceInfo getSourceInfo() {
    return checkedExpr.getSourceInfo();
  }

  /**
   * Returns the underlying {@link com.google.api.expr.ParsedExpr} representation of the abstract
   * syntax tree.
   */
  public ParsedExpr toParsedExpr() {
    return ParsedExpr.newBuilder().setExpr(getExpr()).setSourceInfo(getSourceInfo()).build();
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

  Type getType(long exprId) {
    return types.get(exprId);
  }

  Type getType(ExprOrBuilder expr) {
    return getType(expr.getId());
  }

  Optional<Constant> findEnumValue(long exprId) {
    Reference ref = references.get(exprId);
    return ref != null && ref.hasValue() ? Optional.of(ref.getValue()) : Optional.empty();
  }

  Optional<ImmutableList<String>> findOverloadIDs(long exprId) {
    Reference ref = references.get(exprId);
    return ref != null && !ref.hasValue()
        ? Optional.of(ImmutableList.copyOf(ref.getOverloadIdList()))
        : Optional.empty();
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.CheckedExpr}. */
  public static CelAbstractSyntaxTree fromCheckedExpr(CheckedExpr checkedExpr) {
    return new CelAbstractSyntaxTree(
        checkedExpr,
        CelSource.newBuilder()
            .addAllLineOffsets(checkedExpr.getSourceInfo().getLineOffsetsList())
            .setDescription(checkedExpr.getSourceInfo().getLocation())
            .build());
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.ParsedExpr}. */
  public static CelAbstractSyntaxTree fromParsedExpr(ParsedExpr parsedExpr) {
    return new CelAbstractSyntaxTree(
        parsedExpr,
        CelSource.newBuilder()
            .addAllLineOffsets(parsedExpr.getSourceInfo().getLineOffsetsList())
            .setDescription(parsedExpr.getSourceInfo().getLocation())
            .build());
  }
}
