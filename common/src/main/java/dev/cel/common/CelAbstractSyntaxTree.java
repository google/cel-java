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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.InlineMe;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExprConverter;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.SimpleType;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Represents a checked or unchecked expression, its source, and related metadata such as source
 * position information.
 */
@Immutable
public final class CelAbstractSyntaxTree {

  private final CheckedExpr checkedExpr;
  private final CelSource source;

  private final CelExpr celExpr;

  private final ImmutableMap<Long, CelReference> references;

  private final ImmutableMap<Long, CelType> types;

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
    this.celExpr = CelExprConverter.fromExpr(checkedExpr.getExpr());
    this.source = source;
    this.references =
        checkedExpr.getReferenceMapMap().entrySet().stream()
            .collect(
                toImmutableMap(
                    Entry::getKey,
                    v -> CelExprConverter.exprReferenceToCelReference(v.getValue())));
    this.types =
        checkedExpr.getTypeMapMap().entrySet().stream()
            .collect(toImmutableMap(Entry::getKey, v -> CelTypes.typeToCelType(v.getValue())));
  }

  /**
   * Returns the underlying {@link com.google.api.expr.Expr} representation of the abstract syntax
   * tree.
   *
   * @deprecated Use the renamed {@link #getProtoExpr()} instead
   */
  @Deprecated
  @InlineMe(replacement = "this.getProtoExpr()")
  public Expr getExpr() {
    return getProtoExpr();
  }

  /**
   * Returns the underlying {@link com.google.api.expr.Expr} representation of the abstract syntax
   * tree.
   */
  public Expr getProtoExpr() {
    return checkedExpr.getExpr();
  }

  /**
   * Returns the underlying {@link CelExpr} representation of the abstract syntax tree.
   * TODO: Rename to getExpr
   */
  public CelExpr getCelExpr() {
    return celExpr;
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
    return isChecked() ? getType(getCelExpr().id()).get() : SimpleType.DYN;
  }

  /**
   * For a type checked abstract syntax tree the resulting type is returned in proto format
   * described in checked.proto. Otherwise, the dynamic type is returned.
   */
  public Type getProtoResultType() {
    return CelTypes.celTypeToType(getResultType());
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

  public Optional<CelType> getType(long exprId) {
    return Optional.ofNullable(types.get(exprId));
  }

  public Optional<CelReference> getReference(long exprId) {
    return Optional.ofNullable(references.get(exprId));
  }

  public CelReference getReferenceOrThrow(long exprId) {
    return getReference(exprId)
        .orElseThrow(() -> new NoSuchElementException("Expr Id not found: " + exprId));
  }

  Optional<CelConstant> findEnumValue(long exprId) {
    CelReference ref = references.get(exprId);
    return ref != null ? ref.value() : Optional.empty();
  }

  Optional<ImmutableList<String>> findOverloadIDs(long exprId) {
    CelReference ref = references.get(exprId);
    return ref != null && !ref.value().isPresent()
        ? Optional.of(ref.overloadIds())
        : Optional.empty();
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.CheckedExpr}. */
  public static CelAbstractSyntaxTree fromCheckedExpr(CheckedExpr checkedExpr) {
    return new CelAbstractSyntaxTree(
        checkedExpr,
        CelSource.newBuilder()
            .addAllLineOffsets(checkedExpr.getSourceInfo().getLineOffsetsList())
            .addPositionsMap(checkedExpr.getSourceInfo().getPositionsMap())
            .setDescription(checkedExpr.getSourceInfo().getLocation())
            .build());
  }

  /** Construct an abstract syntax tree from a {@link com.google.api.expr.ParsedExpr}. */
  public static CelAbstractSyntaxTree fromParsedExpr(ParsedExpr parsedExpr) {
    return new CelAbstractSyntaxTree(
        parsedExpr,
        CelSource.newBuilder()
            .addAllLineOffsets(parsedExpr.getSourceInfo().getLineOffsetsList())
            .addPositionsMap(parsedExpr.getSourceInfo().getPositionsMap())
            .setDescription(parsedExpr.getSourceInfo().getLocation())
            .build());
  }
}
