// Copyright 2026 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;
import dev.cel.common.navigation.CelNavigableExpr;
import java.util.Optional;

/**
 * Represents a {@code cel.@block} expression.
 *
 * <p>CEL Block is used by the CSE (Common Subexpression Elimination) optimizer to hoist common
 * subexpressions into an evaluated block.
 */
@Internal
public final class CelBlock {
  public static final String FUNCTION_NAME = "cel.@block";
  public static final String INDEX_PREFIX = "@index";

  private final CelExpr blockExpr;

  private CelBlock(CelExpr blockExpr) {
    this.blockExpr = blockExpr;
  }

  public ImmutableList<CelExpr> indices() {
    return blockExpr.call().args().get(0).list().elements();
  }

  public CelExpr result() {
    return blockExpr.call().args().get(1);
  }

  public CelExpr expr() {
    return blockExpr;
  }

  /**
   * Extracts a {@link CelBlock} from the given AST.
   *
   * <p>Enforces the contract that {@code cel.@block} must only appear exactly once and at the root
   * of the AST.
   *
   * @throws IllegalArgumentException if the block is malformed or its indices are invalid.
   */
  public static Optional<CelBlock> extract(CelAbstractSyntaxTree ast) {
    CelNavigableExpr celNavigableExpr = CelNavigableExpr.fromExpr(ast.getExpr());

    ImmutableList<CelExpr> allCelBlocks =
        celNavigableExpr
            .allNodes()
            .map(CelNavigableExpr::expr)
            .filter(expr -> expr.callOrDefault().function().equals(FUNCTION_NAME))
            .collect(toImmutableList());
    if (allCelBlocks.isEmpty()) {
      return Optional.empty();
    }

    Preconditions.checkArgument(
        allCelBlocks.size() == 1,
        "Expected 1 cel.block function to be present but found %s",
        allCelBlocks.size());
    Preconditions.checkArgument(
        celNavigableExpr.expr().equals(allCelBlocks.get(0)),
        "Expected cel.block to be present at root");

    return Optional.of(fromExpr(allCelBlocks.get(0)));
  }

  /**
   * Constructs a {@link CelBlock} from a {@link CelExpr}.
   *
   * @throws IllegalArgumentException if the expression is not a valid block.
   */
  private static CelBlock fromExpr(CelExpr expr) {
    Preconditions.checkArgument(
        expr.exprKind().getKind() == CelExpr.ExprKind.Kind.CALL,
        "Expected cel.@block to be a call expression");
    Preconditions.checkArgument(
        expr.call().function().equals(FUNCTION_NAME), "Expected function to be cel.@block");
    Preconditions.checkArgument(
        expr.call().args().size() == 2, "Expected exactly 2 arguments for cel.@block");
    Preconditions.checkArgument(
        expr.call().args().get(0).exprKind().getKind() == CelExpr.ExprKind.Kind.LIST,
        "Expected first argument of cel.@block to be a list");

    CelBlock block = new CelBlock(expr);

    // Assert correctness on block indices used in subexpressions
    ImmutableList<CelExpr> subexprs = block.indices();
    for (int i = 0; i < subexprs.size(); i++) {
      verifyBlockIndex(subexprs.get(i), i, expr);
    }

    // Assert correctness on block indices used in block result
    CelExpr blockResult = block.result();
    verifyBlockIndex(blockResult, subexprs.size(), expr);
    boolean resultHasAtLeastOneBlockIndex =
        CelNavigableExpr.fromExpr(blockResult)
            .allNodes()
            .map(CelNavigableExpr::expr)
            .anyMatch(e -> e.identOrDefault().name().startsWith(INDEX_PREFIX));
    Preconditions.checkArgument(
        resultHasAtLeastOneBlockIndex,
        "Expected at least one reference of index in cel.block result");

    return block;
  }

  private static void verifyBlockIndex(CelExpr celExpr, int maxIndexValue, CelExpr rootBlock) {
    boolean areAllIndicesValid =
        CelNavigableExpr.fromExpr(celExpr)
            .allNodes()
            .map(CelNavigableExpr::expr)
            .filter(expr -> expr.identOrDefault().name().startsWith(INDEX_PREFIX))
            .map(CelExpr::ident)
            .allMatch(
                blockIdent ->
                    Integer.parseInt(blockIdent.name().substring(INDEX_PREFIX.length()))
                        < maxIndexValue);
    Preconditions.checkArgument(
        areAllIndicesValid,
        "Illegal block index found. The index value must be less than %s. Expr: %s",
        maxIndexValue,
        rootBlock);
  }
}
