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

import static java.lang.Math.max;

import com.google.common.collect.ImmutableList;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import java.util.stream.Stream;

/** Visitor implementation to navigate an AST. */
final class CelNavigableExprVisitor {
  private static final int MAX_DESCENDANTS_RECURSION_DEPTH = 500;

  private final Stream.Builder<CelNavigableExpr.Builder> streamBuilder;
  private final TraversalOrder traversalOrder;
  private final int maxDepth;

  private CelNavigableExprVisitor(int maxDepth, TraversalOrder traversalOrder) {
    this.maxDepth = maxDepth;
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
  static Stream<CelNavigableExpr> collect(
      CelNavigableExpr navigableExpr, TraversalOrder traversalOrder) {
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
  static Stream<CelNavigableExpr> collect(
      CelNavigableExpr navigableExpr, int maxDepth, TraversalOrder traversalOrder) {
    CelNavigableExprVisitor visitor = new CelNavigableExprVisitor(maxDepth, traversalOrder);

    visitor.visit(navigableExpr.toBuilder());

    return visitor.streamBuilder.build().map(CelNavigableExpr.Builder::build);
  }

  private int visit(CelNavigableExpr.Builder navigableExpr) {
    if (navigableExpr.depth() > MAX_DESCENDANTS_RECURSION_DEPTH - 1) {
      throw new IllegalStateException("Max recursion depth reached.");
    }
    if (navigableExpr.depth() > maxDepth) {
      return -1;
    }
    if (traversalOrder.equals(TraversalOrder.PRE_ORDER)) {
      streamBuilder.add(navigableExpr);
    }

    int height = 1;
    switch (navigableExpr.getKind()) {
      case CALL:
        height += visit(navigableExpr, navigableExpr.expr().call());
        break;
      case CREATE_LIST:
        height += visit(navigableExpr, navigableExpr.expr().createList());
        break;
      case SELECT:
        height += visit(navigableExpr, navigableExpr.expr().select());
        break;
      case CREATE_STRUCT:
        height += visitStruct(navigableExpr, navigableExpr.expr().createStruct());
        break;
      case CREATE_MAP:
        height += visitMap(navigableExpr, navigableExpr.expr().createMap());
        break;
      case COMPREHENSION:
        height += visit(navigableExpr, navigableExpr.expr().comprehension());
        break;
      default:
        // This is a leaf node
        height = 0;
        break;
    }

    navigableExpr.setHeight(height);
    if (traversalOrder.equals(TraversalOrder.POST_ORDER)) {
      streamBuilder.add(navigableExpr);
    }

    return height;
  }

  private int visit(CelNavigableExpr.Builder navigableExpr, CelCall call) {
    int targetHeight = 0;
    if (call.target().isPresent()) {
      CelNavigableExpr.Builder target = newNavigableChild(navigableExpr, call.target().get());
      targetHeight = visit(target);
    }

    int argumentHeight = visitExprList(call.args(), navigableExpr);
    return max(targetHeight, argumentHeight);
  }

  private int visit(CelNavigableExpr.Builder navigableExpr, CelCreateList createList) {
    return visitExprList(createList.elements(), navigableExpr);
  }

  private int visit(CelNavigableExpr.Builder navigableExpr, CelSelect selectExpr) {
    CelNavigableExpr.Builder operand = newNavigableChild(navigableExpr, selectExpr.operand());
    return visit(operand);
  }

  private int visit(CelNavigableExpr.Builder navigableExpr, CelComprehension comprehension) {
    int maxHeight = 0;
    maxHeight = max(visit(newNavigableChild(navigableExpr, comprehension.iterRange())), maxHeight);
    maxHeight = max(visit(newNavigableChild(navigableExpr, comprehension.accuInit())), maxHeight);
    maxHeight =
        max(visit(newNavigableChild(navigableExpr, comprehension.loopCondition())), maxHeight);
    maxHeight = max(visit(newNavigableChild(navigableExpr, comprehension.loopStep())), maxHeight);
    maxHeight = max(visit(newNavigableChild(navigableExpr, comprehension.result())), maxHeight);

    return maxHeight;
  }

  private int visitStruct(CelNavigableExpr.Builder navigableExpr, CelCreateStruct struct) {
    int maxHeight = 0;
    for (CelCreateStruct.Entry entry : struct.entries()) {
      CelNavigableExpr.Builder value = newNavigableChild(navigableExpr, entry.value());
      maxHeight = max(visit(value), maxHeight);
    }
    return maxHeight;
  }

  private int visitMap(CelNavigableExpr.Builder navigableExpr, CelCreateMap map) {
    int maxHeight = 0;
    for (CelCreateMap.Entry entry : map.entries()) {
      CelNavigableExpr.Builder key = newNavigableChild(navigableExpr, entry.key());
      maxHeight = max(visit(key), maxHeight);

      CelNavigableExpr.Builder value = newNavigableChild(navigableExpr, entry.value());
      maxHeight = max(visit(value), maxHeight);
    }
    return 0;
  }

  private int visitExprList(
      ImmutableList<CelExpr> createListExpr, CelNavigableExpr.Builder parent) {
    int maxHeight = 0;
    for (CelExpr expr : createListExpr) {
      CelNavigableExpr.Builder arg = newNavigableChild(parent, expr);
      maxHeight = max(visit(arg), maxHeight);
    }
    return maxHeight;
  }

  private CelNavigableExpr.Builder newNavigableChild(
      CelNavigableExpr.Builder parent, CelExpr expr) {
    return CelNavigableExpr.builder()
        .setExpr(expr)
        .setDepth(parent.depth() + 1)
        .setParentBuilder(parent);
  }
}
