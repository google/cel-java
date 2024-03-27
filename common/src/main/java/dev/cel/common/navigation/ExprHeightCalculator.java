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

import dev.cel.common.ast.MutableExpr;
import dev.cel.common.ast.MutableExpr.MutableCall;
import dev.cel.common.ast.MutableExpr.MutableComprehension;
import dev.cel.common.ast.MutableExpr.MutableCreateList;
import dev.cel.common.ast.MutableExpr.MutableCreateMap;
import dev.cel.common.ast.MutableExpr.MutableCreateStruct;
import dev.cel.common.ast.MutableExpr.MutableSelect;
import java.util.HashMap;
import java.util.List;

/** Package-private class to assist computing the height of expression nodes. */
final class ExprHeightCalculator {
  // Store hashmap instead of immutable map for performance, such that this helper class can be
  // instantiated faster.
  private final HashMap<Long, Integer> idToHeight;

  ExprHeightCalculator(MutableExpr celExpr) {
    this.idToHeight = new HashMap<>();
    visit(celExpr);
  }

  int getHeight(Long exprId) {
    if (!idToHeight.containsKey(exprId)) {
      throw new IllegalStateException("Height not found for expression id: " + exprId);
    }

    return idToHeight.get(exprId);
  }

  private int visit(MutableExpr celExpr) {
    int height = 1;
    switch (celExpr.exprKind()) {
      case CALL:
        height += visit(celExpr.call());
        break;
      case CREATE_LIST:
        height += visit(celExpr.createList());
        break;
      case SELECT:
        height += visit(celExpr.select());
        break;
      case CREATE_STRUCT:
        height += visitStruct(celExpr.createStruct());
        break;
      case CREATE_MAP:
        height += visitMap(celExpr.createMap());
        break;
      case COMPREHENSION:
        height += visit(celExpr.comprehension());
        break;
      default:
        // This is a leaf node
        height = 0;
        break;
    }

    idToHeight.put(celExpr.id(), height);
    return height;
  }

  private int visit(MutableCall call) {
    int targetHeight = 0;
    if (call.target().isPresent()) {
      targetHeight = visit(call.target().get());
    }

    int argumentHeight = visitExprList(call.args());
    return max(targetHeight, argumentHeight);
  }

  private int visit(MutableCreateList createList) {
    return visitExprList(createList.elements());
  }

  private int visit(MutableSelect selectExpr) {
    return visit(selectExpr.operand());
  }

  private int visit(MutableComprehension comprehension) {
    int maxHeight = 0;
    maxHeight = max(visit(comprehension.iterRange()), maxHeight);
    maxHeight = max(visit(comprehension.accuInit()), maxHeight);
    maxHeight = max(visit(comprehension.loopCondition()), maxHeight);
    maxHeight = max(visit(comprehension.loopStep()), maxHeight);
    maxHeight = max(visit(comprehension.result()), maxHeight);

    return maxHeight;
  }

  private int visitStruct(MutableCreateStruct struct) {
    int maxHeight = 0;
    for (MutableCreateStruct.Entry entry : struct.entries()) {
      maxHeight = max(visit(entry.value()), maxHeight);
    }
    return maxHeight;
  }

  private int visitMap(MutableCreateMap map) {
    int maxHeight = 0;
    for (MutableCreateMap.Entry entry : map.entries()) {
      maxHeight = max(visit(entry.key()), maxHeight);
      maxHeight = max(visit(entry.value()), maxHeight);
    }
    return maxHeight;
  }

  private int visitExprList(List<MutableExpr> createListExpr) {
    int maxHeight = 0;
    for (MutableExpr expr : createListExpr) {
      maxHeight = max(visit(expr), maxHeight);
    }
    return maxHeight;
  }
}
