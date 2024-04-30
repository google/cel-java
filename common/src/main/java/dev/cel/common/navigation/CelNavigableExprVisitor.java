// Copyright 2023 Google LLC
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

package dev.cel.common.navigation;

import dev.cel.common.ast.Expression;
import dev.cel.common.ast.Expression.List;
import dev.cel.common.navigation.ExprPropertyCalculator.ExprProperty;
import java.util.stream.Stream;

/** Visitor implementation to navigate an AST. */
final class CelNavigableExprVisitor<E extends Expression, T extends BaseNavigableExpr<E>> {
  private static final int MAX_DESCENDANTS_RECURSION_DEPTH = 500;

  private final Stream.Builder<T> streamBuilder;
  private final ExprPropertyCalculator<E> exprPropertyCalculator;
  private final TraversalOrder traversalOrder;
  private final int maxDepth;

  private CelNavigableExprVisitor(
      int maxDepth,
      ExprPropertyCalculator<E> exprPropertyCalculator,
      TraversalOrder traversalOrder) {
    this.maxDepth = maxDepth;
    this.exprPropertyCalculator = exprPropertyCalculator;
    this.traversalOrder = traversalOrder;
    this.streamBuilder = Stream.builder();
  }

  /**
   * Returns a stream containing all the nodes using the specified traversal order. Note that the
   * collection occurs eagerly.
   *
   * <pre>
   * For example, given the following tree:
   *             a
   *          b     c
   *       d    e
   *
   * The collected nodes are as follows using pre-order traversal:
   *
   * maxDepth of -1: none
   * maxDepth of 0: a
   * maxDepth of 1: a, b, c
   * maxDepth of 2: a, b, d, e, c
   * </pre>
   */
  static <E extends Expression, T extends BaseNavigableExpr<E>> Stream<T> collect(
      T navigableExpr, TraversalOrder traversalOrder) {
    return collect(navigableExpr, MAX_DESCENDANTS_RECURSION_DEPTH, traversalOrder);
  }

  /**
   * Returns a stream containing all the nodes upto the maximum depth specified using the specified
   * traversal order. Note that the collection occurs eagerly.
   *
   * <pre>
   * For example, given the following tree:
   *             a
   *          b     c
   *       d    e
   *
   * The collected nodes are as follows using pre-order traversal:
   *
   * maxDepth of -1: none
   * maxDepth of 0: a
   * maxDepth of 1: a, b, c
   * maxDepth of 2: a, b, d, e, c
   * </pre>
   */
  static <E extends Expression, T extends BaseNavigableExpr<E>> Stream<T> collect(
      T navigableExpr, int maxDepth, TraversalOrder traversalOrder) {
    ExprPropertyCalculator<E> exprHeightCalculator =
        new ExprPropertyCalculator<>(navigableExpr.expr());
    CelNavigableExprVisitor<E, T> visitor =
        new CelNavigableExprVisitor<>(maxDepth, exprHeightCalculator, traversalOrder);

    visitor.visit(navigableExpr);

    return visitor.streamBuilder.build();
  }

  private void visit(T navigableExpr) {
    if (navigableExpr.depth() > MAX_DESCENDANTS_RECURSION_DEPTH - 1) {
      throw new IllegalStateException("Max recursion depth reached.");
    }

    boolean addToStream = navigableExpr.depth() <= maxDepth;
    if (addToStream && traversalOrder.equals(TraversalOrder.PRE_ORDER)) {
      streamBuilder.add(navigableExpr);
    }

    switch (navigableExpr.getKind()) {
      case CALL:
        visit(navigableExpr, navigableExpr.expr().call());
        break;
      case LIST:
        visit(navigableExpr, navigableExpr.expr().createList());
        break;
      case SELECT:
        visit(navigableExpr, navigableExpr.expr().select());
        break;
      case STRUCT:
        visitStruct(navigableExpr, navigableExpr.expr().createStruct());
        break;
      case MAP:
        visitMap(navigableExpr, navigableExpr.expr().createMap());
        break;
      case COMPREHENSION:
        visit(navigableExpr, navigableExpr.expr().comprehension());
        break;
      default:
        break;
    }

    if (addToStream && traversalOrder.equals(TraversalOrder.POST_ORDER)) {
      streamBuilder.add(navigableExpr);
    }
  }

  private void visit(T navigableExpr, Expression.Call<E> call) {
    if (call.target().isPresent()) {
      visit(newNavigableChild(navigableExpr, call.target().get()));
    }

    visitExprList(call.args(), navigableExpr);
  }

  private void visit(T navigableExpr, List<E> createList) {
    visitExprList(createList.elements(), navigableExpr);
  }

  private void visit(T navigableExpr, Expression.Select<E> selectExpr) {
    T operand = newNavigableChild(navigableExpr, selectExpr.operand());
    visit(operand);
  }

  private void visit(T navigableExpr, Expression.Comprehension<E> comprehension) {
    visit(newNavigableChild(navigableExpr, comprehension.iterRange()));
    visit(newNavigableChild(navigableExpr, comprehension.accuInit()));
    visit(newNavigableChild(navigableExpr, comprehension.loopCondition()));
    visit(newNavigableChild(navigableExpr, comprehension.loopStep()));
    visit(newNavigableChild(navigableExpr, comprehension.result()));
  }

  private void visitStruct(T navigableExpr, Expression.Struct<Expression.Struct.Entry<E>> struct) {
    for (Expression.Struct.Entry<E> entry : struct.entries()) {
      visit(newNavigableChild(navigableExpr, entry.value()));
    }
  }

  private void visitMap(T navigableExpr, Expression.Map<Expression.Map.Entry<E>> map) {
    for (Expression.Map.Entry<E> entry : map.entries()) {
      T key = newNavigableChild(navigableExpr, entry.key());
      visit(key);

      T value = newNavigableChild(navigableExpr, entry.value());
      visit(value);
    }
  }

  private void visitExprList(java.util.List<E> createListExpr, T parent) {
    for (E expr : createListExpr) {
      visit(newNavigableChild(parent, expr));
    }
  }

  private T newNavigableChild(T parent, E expr) {
    ExprProperty exprProperty = exprPropertyCalculator.getProperty(expr.id());

    return parent
        .<T>builderFromInstance()
        .setExpr(expr)
        .setDepth(parent.depth() + 1)
        .setHeight(exprProperty.height())
        .setMaxId(exprProperty.maxId())
        .setParent(parent)
        .build();
  }
}
