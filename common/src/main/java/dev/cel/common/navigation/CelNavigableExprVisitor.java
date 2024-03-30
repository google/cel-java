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

import com.google.common.collect.ImmutableList;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.MutableExpr;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import dev.cel.common.ast.MutableExpr.MutableCall;
import dev.cel.common.ast.MutableExpr.MutableComprehension;
import dev.cel.common.ast.MutableExpr.MutableCreateList;
import dev.cel.common.ast.MutableExpr.MutableCreateMap;
import dev.cel.common.ast.MutableExpr.MutableCreateStruct;
import dev.cel.common.ast.MutableExpr.MutableSelect;

import java.util.List;
import java.util.stream.Stream;

/** Visitor implementation to navigate an AST. */
final class CelNavigableExprVisitor {
  private static final int MAX_DESCENDANTS_RECURSION_DEPTH = 500;

  private final Stream.Builder<CelNavigableExpr> streamBuilder;
  private final ExprHeightCalculator exprHeightCalculator;
  private final TraversalOrder traversalOrder;
  private final int maxDepth;

  private CelNavigableExprVisitor(
      int maxDepth, ExprHeightCalculator exprHeightCalculator, TraversalOrder traversalOrder) {
    this.maxDepth = maxDepth;
    this.exprHeightCalculator = exprHeightCalculator;
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
    ExprHeightCalculator exprHeightCalculator = new ExprHeightCalculator(navigableExpr.mutableExpr());
    CelNavigableExprVisitor visitor =
        new CelNavigableExprVisitor(maxDepth, exprHeightCalculator, traversalOrder);

    // TODO
    if (navigableExpr.mutableExpr().exprKind().equals(Kind.NOT_SET)) {
      visitor.visit(navigableExpr);
    } else {
      visitor.visitMutable(navigableExpr);
    }

    return visitor.streamBuilder.build();
  }

  private void visitMutable(CelNavigableExpr navigableExpr) {
    if (navigableExpr.depth() > MAX_DESCENDANTS_RECURSION_DEPTH - 1) {
      throw new IllegalStateException("Max recursion depth reached.");
    }

    boolean addToStream = navigableExpr.depth() <= maxDepth;
    if (addToStream && traversalOrder.equals(TraversalOrder.PRE_ORDER)) {
      streamBuilder.add(navigableExpr);
    }

    MutableExpr mutableExpr = navigableExpr.mutableExpr();
    switch (navigableExpr.getKind()) {
      case CALL:
        visitMutable(navigableExpr, mutableExpr.call());
        break;
      case CREATE_LIST:
        visitMutable(navigableExpr, mutableExpr.createList());
        break;
      case SELECT:
        visitMutable(navigableExpr, mutableExpr.select());
        break;
      case CREATE_STRUCT:
        visitMutableStruct(navigableExpr, mutableExpr.createStruct());
        break;
      case CREATE_MAP:
        visitMutableMap(navigableExpr, mutableExpr.createMap());
        break;
      case COMPREHENSION:
        visitMutable(navigableExpr, mutableExpr.comprehension());
        break;
      default:
        break;
    }

    if (addToStream && traversalOrder.equals(TraversalOrder.POST_ORDER)) {
      streamBuilder.add(navigableExpr);
    }
  }

  private void visit(CelNavigableExpr navigableExpr) {
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
      case CREATE_LIST:
        visit(navigableExpr, navigableExpr.expr().createList());
        break;
      case SELECT:
        visit(navigableExpr, navigableExpr.expr().select());
        break;
      case CREATE_STRUCT:
        visitStruct(navigableExpr, navigableExpr.expr().createStruct());
        break;
      case CREATE_MAP:
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

  private void visit(CelNavigableExpr navigableExpr, CelCall call) {
    if (call.target().isPresent()) {
      visit(newNavigableChild(navigableExpr, call.target().get()));
    }

    visitExprList(call.args(), navigableExpr);
  }

  private void visit(CelNavigableExpr navigableExpr, CelCreateList createList) {
    visitExprList(createList.elements(), navigableExpr);
  }

  private void visit(CelNavigableExpr navigableExpr, CelSelect selectExpr) {
    CelNavigableExpr operand = newNavigableChild(navigableExpr, selectExpr.operand());
    visit(operand);
  }

  private void visit(CelNavigableExpr navigableExpr, CelComprehension comprehension) {
    visit(newNavigableChild(navigableExpr, comprehension.iterRange()));
    visit(newNavigableChild(navigableExpr, comprehension.accuInit()));
    visit(newNavigableChild(navigableExpr, comprehension.loopCondition()));
    visit(newNavigableChild(navigableExpr, comprehension.loopStep()));
    visit(newNavigableChild(navigableExpr, comprehension.result()));
  }

  private void visitStruct(CelNavigableExpr navigableExpr, CelCreateStruct struct) {
    for (CelCreateStruct.Entry entry : struct.entries()) {
      visit(newNavigableChild(navigableExpr, entry.value()));
    }
  }

  private void visitMap(CelNavigableExpr navigableExpr, CelCreateMap map) {
    for (CelCreateMap.Entry entry : map.entries()) {
      CelNavigableExpr key = newNavigableChild(navigableExpr, entry.key());
      visit(key);

      CelNavigableExpr value = newNavigableChild(navigableExpr, entry.value());
      visit(value);
    }
  }

  private void visitExprList(ImmutableList<CelExpr> createListExpr, CelNavigableExpr parent) {
    for (CelExpr expr : createListExpr) {
      visit(newNavigableChild(parent, expr));
    }
  }

  private void visitMutable(CelNavigableExpr navigableExpr, MutableCall call) {
    if (call.target().isPresent()) {
      visitMutable(newNavigableChild(navigableExpr, call.target().get()));
    }

    visitMutableExprList(call.args(), navigableExpr);
  }

  private void visitMutable(CelNavigableExpr navigableExpr, MutableCreateList createList) {
    visitMutableExprList(createList.elements(), navigableExpr);
  }

  private void visitMutable(CelNavigableExpr navigableExpr, MutableSelect selectExpr) {
    CelNavigableExpr operand = newNavigableChild(navigableExpr, selectExpr.operand());
    visitMutable(operand);
  }

  private void visitMutable(CelNavigableExpr navigableExpr, MutableComprehension comprehension) {
    visitMutable(newNavigableChild(navigableExpr, comprehension.iterRange()));
    visitMutable(newNavigableChild(navigableExpr, comprehension.accuInit()));
    visitMutable(newNavigableChild(navigableExpr, comprehension.loopCondition()));
    visitMutable(newNavigableChild(navigableExpr, comprehension.loopStep()));
    visitMutable(newNavigableChild(navigableExpr, comprehension.result()));
  }

  private void visitMutableStruct(CelNavigableExpr navigableExpr, MutableCreateStruct struct) {
    for (MutableCreateStruct.Entry entry : struct.entries()) {
      visitMutable(newNavigableChild(navigableExpr, entry.value()));
    }
  }

  private void visitMutableMap(CelNavigableExpr navigableExpr, MutableCreateMap map) {
    for (MutableCreateMap.Entry entry : map.entries()) {
      CelNavigableExpr key = newNavigableChild(navigableExpr, entry.key());
      visitMutable(key);

      CelNavigableExpr value = newNavigableChild(navigableExpr, entry.value());
      visitMutable(value);
    }
  }

  private void visitMutableExprList(List<MutableExpr> createListExpr, CelNavigableExpr parent) {
    for (MutableExpr expr : createListExpr) {
      visitMutable(newNavigableChild(parent, expr));
    }
  }

  private CelNavigableExpr newNavigableChild(CelNavigableExpr parent, MutableExpr expr) {
    CelNavigableExpr.Builder navigableExpr =
            CelNavigableExpr.builder()
                    .setMutableExpr(expr)
                    .setDepth(parent.depth() + 1)
                   .setHeight(exprHeightCalculator.getHeight(expr.id()))
                    .setParent(parent);

    return navigableExpr.build();
  }

  private CelNavigableExpr newNavigableChild(CelNavigableExpr parent, CelExpr expr) {
    CelNavigableExpr.Builder navigableExpr =
        CelNavigableExpr.builder()
            .setExpr(expr)
            .setDepth(parent.depth() + 1)
           // .setHeight(exprHeightCalculator.getHeight(expr.id()))
            .setParent(parent);

    return navigableExpr.build();
  }
}
