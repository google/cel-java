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

import dev.cel.expr.Type;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.SimpleType;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Represents a checked or unchecked expression, its source, and related metadata such as source
 * position information.
 *
 * <p>Note: Use {@link CelProtoAbstractSyntaxTree} if you need access to the protobuf equivalent
 * ASTs, such as ParsedExpr and CheckedExpr from syntax.proto or checked.proto.
 */
@AutoValue
@Immutable
public abstract class CelAbstractSyntaxTree {

  abstract CelSource celSource();

  abstract CelExpr celExpr();

  abstract ImmutableMap<Long, CelReference> references();

  abstract ImmutableMap<Long, CelType> types();

  /**
   * Constructs a new instance of CelAbstractSyntaxTree that represent a parsed expression.
   *
   * <p>Note that ASTs should not be manually constructed except for special circumstances such as
   * validating or optimizing an AST.
   */
  public static CelAbstractSyntaxTree newParsedAst(CelExpr celExpr, CelSource celSource) {
    return new AutoValue_CelAbstractSyntaxTree(
        celSource, celExpr, ImmutableMap.of(), ImmutableMap.of());
  }

  /**
   * Constructs a new instance of CelAbstractSyntaxTree that represent a checked expression.
   *
   * <p>CEL Library Internals. Do not construct a type-checked AST by hand. Use a CelCompiler to
   * type-check a parsed AST instead.
   */
  @Internal
  public static CelAbstractSyntaxTree newCheckedAst(
      CelExpr celExpr,
      CelSource celSource,
      Map<Long, CelReference> references,
      Map<Long, CelType> types) {
    return new AutoValue_CelAbstractSyntaxTree(
        celSource, celExpr, ImmutableMap.copyOf(references), ImmutableMap.copyOf(types));
  }

  /** Returns the underlying {@link CelExpr} representation of the abstract syntax tree. */
  public CelExpr getExpr() {
    return celExpr();
  }

  /** Tests whether the underlying abstract syntax tree has been type checked or not. */
  public boolean isChecked() {
    return !types().isEmpty();
  }

  /**
   * For a type checked abstract syntax tree the resulting type is returned. Otherwise, the dynamic
   * type is returned.
   */
  public CelType getResultType() {
    return isChecked() ? getType(getExpr().id()).get() : SimpleType.DYN;
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
    return celSource();
  }

  public Optional<CelType> getType(long exprId) {
    return Optional.ofNullable(types().get(exprId));
  }

  public ImmutableMap<Long, CelType> getTypeMap() {
    return types();
  }

  public Optional<CelReference> getReference(long exprId) {
    return Optional.ofNullable(references().get(exprId));
  }

  public ImmutableMap<Long, CelReference> getReferenceMap() {
    return references();
  }

  public CelReference getReferenceOrThrow(long exprId) {
    return getReference(exprId)
        .orElseThrow(() -> new NoSuchElementException("Expr Id not found: " + exprId));
  }

  Optional<CelConstant> findEnumValue(long exprId) {
    CelReference ref = references().get(exprId);
    return ref != null ? ref.value() : Optional.empty();
  }

  Optional<ImmutableList<String>> findOverloadIDs(long exprId) {
    CelReference ref = references().get(exprId);
    return ref != null && !ref.value().isPresent()
        ? Optional.of(ref.overloadIds())
        : Optional.empty();
  }
}
