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

import dev.cel.common.ast.Expression;
import dev.cel.common.ast.Expression.CreateMap;
import dev.cel.common.ast.Expression.CreateStruct;
import java.util.HashMap;
import java.util.List;

/** Package-private class to assist computing the height of expression nodes. */
final class ExprHeightCalculator<E extends Expression> {
  // Store hashmap instead of immutable map for performance, such that this helper class can be
  // instantiated faster.
  private final HashMap<Long, Integer> idToHeight;

  ExprHeightCalculator(E expr) {
    this.idToHeight = new HashMap<>();
    visit(expr);
  }

  int getHeight(Long exprId) {
    if (!idToHeight.containsKey(exprId)) {
      throw new IllegalStateException("Height not found for expression id: " + exprId);
    }

    return idToHeight.get(exprId);
  }

  private int visit(E expr) {
    int height = 1;
    switch (expr.getKind()) {
      case CALL:
        height += visit(expr.call());
        break;
      case CREATE_LIST:
        height += visit(expr.createList());
        break;
      case SELECT:
        height += visit(expr.select());
        break;
      case CREATE_STRUCT:
        height += visitStruct(expr.createStruct());
        break;
      case CREATE_MAP:
        height += visitMap(expr.createMap());
        break;
      case COMPREHENSION:
        height += visit(expr.comprehension());
        break;
      default:
        // This is a leaf node
        height = 0;
        break;
    }

    idToHeight.put(expr.id(), height);
    return height;
  }

  private int visit(Expression.Call<E> call) {
    int targetHeight = 0;
    if (call.target().isPresent()) {
      targetHeight = visit(call.target().get());
    }

    int argumentHeight = visitExprList(call.args());
    return max(targetHeight, argumentHeight);
  }

  private int visit(Expression.CreateList<E> createList) {
    return visitExprList(createList.elements());
  }

  private int visit(Expression.Select<E> selectExpr) {
    return visit(selectExpr.operand());
  }

  private int visit(Expression.Comprehension<E> comprehension) {
    int maxHeight = 0;
    maxHeight = max(visit(comprehension.iterRange()), maxHeight);
    maxHeight = max(visit(comprehension.accuInit()), maxHeight);
    maxHeight = max(visit(comprehension.loopCondition()), maxHeight);
    maxHeight = max(visit(comprehension.loopStep()), maxHeight);
    maxHeight = max(visit(comprehension.result()), maxHeight);

    return maxHeight;
  }

  private int visitStruct(Expression.CreateStruct<CreateStruct.Entry<E>> struct) {
    int maxHeight = 0;
    for (CreateStruct.Entry<E> entry : struct.entries()) {
      maxHeight = max(visit(entry.value()), maxHeight);
    }
    return maxHeight;
  }

  private int visitMap(Expression.CreateMap<CreateMap.Entry<E>> map) {
    int maxHeight = 0;
    for (CreateMap.Entry<E> entry : map.entries()) {
      maxHeight = max(visit(entry.key()), maxHeight);
      maxHeight = max(visit(entry.value()), maxHeight);
    }
    return maxHeight;
  }

  private int visitExprList(List<E> createListExpr) {
    int maxHeight = 0;
    for (E expr : createListExpr) {
      maxHeight = max(visit(expr), maxHeight);
    }
    return maxHeight;
  }
}
