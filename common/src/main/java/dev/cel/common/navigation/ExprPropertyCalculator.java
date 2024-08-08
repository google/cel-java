// Copyright 2024 Google LLC
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

import com.google.auto.value.AutoValue;
import dev.cel.common.ast.Expression;
import dev.cel.common.ast.Expression.List;
import dev.cel.common.ast.Expression.Map;
import dev.cel.common.ast.Expression.Struct;
import java.util.HashMap;

/** Package-private class to assist computing the height and the max ID of expression nodes. */
final class ExprPropertyCalculator<E extends Expression> {
  // Store hashmap instead of immutable map for performance, such that this helper class can be
  // instantiated faster.
  private final HashMap<Long, ExprProperty> idToProperty;

  ExprPropertyCalculator(E celExpr) {
    this.idToProperty = new HashMap<>();
    visit(celExpr);
  }

  /**
   * Retrieves the property containing the expression's maximum ID and the height of the subtree.
   *
   * @throws IllegalArgumentException If the provided expression ID does not exist.
   */
  ExprProperty getProperty(Long exprId) {
    if (!idToProperty.containsKey(exprId)) {
      throw new IllegalArgumentException("Property not found for expression id: " + exprId);
    }

    return idToProperty.get(exprId);
  }

  private ExprProperty visit(E expr) {
    int baseHeight = 1;
    ExprProperty visitedProperty;
    switch (expr.getKind()) {
      case CALL:
        visitedProperty = visit(expr.call());
        break;
      case LIST:
        visitedProperty = visit(expr.list());
        break;
      case SELECT:
        visitedProperty = visit(expr.select());
        break;
      case STRUCT:
        visitedProperty = visitStruct(expr.struct());
        break;
      case MAP:
        visitedProperty = visitMap(expr.map());
        break;
      case COMPREHENSION:
        visitedProperty = visit(expr.comprehension());
        break;
      default:
        // This is a leaf node
        baseHeight = 0;
        visitedProperty = ExprProperty.create(baseHeight, expr.id());
        break;
    }

    ExprProperty exprProperty =
        ExprProperty.create(
            baseHeight + visitedProperty.height(), max(visitedProperty.maxId(), expr.id()));
    idToProperty.put(expr.id(), exprProperty);

    return exprProperty;
  }

  private ExprProperty visit(Expression.Call<E> call) {
    ExprProperty visitedTarget = ExprProperty.create(0, 0);
    if (call.target().isPresent()) {
      visitedTarget = visit(call.target().get());
    }

    ExprProperty visitedArgument = visitExprList(call.args());
    return ExprProperty.merge(visitedArgument, visitedTarget);
  }

  private ExprProperty visit(List<E> list) {
    return visitExprList(list.elements());
  }

  private ExprProperty visit(Expression.Select<E> selectExpr) {
    return visit(selectExpr.operand());
  }

  private ExprProperty visit(Expression.Comprehension<E> comprehension) {
    ExprProperty visitedProperty = visit(comprehension.iterRange());
    visitedProperty = ExprProperty.merge(visitedProperty, visit(comprehension.accuInit()));
    visitedProperty = ExprProperty.merge(visitedProperty, visit(comprehension.loopCondition()));
    visitedProperty = ExprProperty.merge(visitedProperty, visit(comprehension.loopStep()));
    visitedProperty = ExprProperty.merge(visitedProperty, visit(comprehension.result()));

    return visitedProperty;
  }

  private ExprProperty visitStruct(Expression.Struct<Struct.Entry<E>> struct) {
    ExprProperty visitedProperty = ExprProperty.create(0, 0);
    for (Struct.Entry<E> entry : struct.entries()) {
      visitedProperty = ExprProperty.merge(visitedProperty, visit(entry.value()));
    }
    return visitedProperty;
  }

  private ExprProperty visitMap(Expression.Map<Map.Entry<E>> map) {
    ExprProperty visitedProperty = ExprProperty.create(0, 0);
    for (Map.Entry<E> entry : map.entries()) {
      visitedProperty = ExprProperty.merge(visitedProperty, visit(entry.key()));
      visitedProperty = ExprProperty.merge(visitedProperty, visit(entry.value()));
    }
    return visitedProperty;
  }

  private ExprProperty visitExprList(java.util.List<E> list) {
    ExprProperty visitedProperty = ExprProperty.create(0, 0);
    for (E expr : list) {
      visitedProperty = ExprProperty.merge(visitedProperty, visit(expr));
    }
    return visitedProperty;
  }

  /** Value class to store the height and the max ID at a specific expression ID. */
  @AutoValue
  abstract static class ExprProperty {
    abstract int height();

    abstract long maxId();

    /** Merges the two {@link ExprProperty}, taking their maximum values from the properties. */
    private static ExprProperty merge(ExprProperty e1, ExprProperty e2) {
      return create(max(e1.height(), e2.height()), max(e1.maxId(), e2.maxId()));
    }

    private static ExprProperty create(int height, long maxId) {
      return new AutoValue_ExprPropertyCalculator_ExprProperty(height, maxId);
    }
  }
}
