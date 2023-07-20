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

package dev.cel.parser;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

import dev.cel.common.ast.CelExpr;
import java.util.ArrayList;

/**
 * Package-private balancer that performs tree balancing on operators whose arguments are of equal
 * precedence.
 *
 * <p>The purpose of the balancer is to ensure a compact serialization format for the logical &&, ||
 * operators which have a tendency to create long DAGs which are skewed in one direction. Since the
 * operators are commutative re-ordering the terms *must not* affect the evaluation result.
 *
 * <p>Based on code from //third_party/cel/go/parser/helper.go and
 * //third_party/cel/cpp/parser/parser.cc
 */
final class ExpressionBalancer {

  private final String function;
  private final ArrayList<CelExpr> terms;
  private final ArrayList<Long> ids;

  ExpressionBalancer(String function, CelExpr expr) {
    checkArgument(!isNullOrEmpty(function));
    this.function = function;
    terms = new ArrayList<>();
    terms.add(checkNotNull(expr));
    ids = new ArrayList<>();
  }

  void add(long operationId, CelExpr expr) {
    checkNotNull(expr);
    checkArgument(operationId > 0L);
    terms.add(expr);
    ids.add(operationId);
  }

  CelExpr balance() {
    // add(long, Expr) should have been called at least once, as the visitor should have terminated
    // early. The following check ensures that is the case.
    checkState(terms.size() > 1);
    return balance(0, ids.size() - 1);
  }

  private CelExpr balance(int lo, int hi) {
    int mid = (lo + hi + 1) / 2;
    CelExpr left;
    if (mid == lo) {
      left = terms.get(mid);
    } else {
      left = balance(lo, mid - 1);
    }
    CelExpr right;
    if (mid == hi) {
      right = terms.get(mid + 1);
    } else {
      right = balance(mid + 1, hi);
    }
    return CelExpr.newBuilder()
        .setId(ids.get(mid))
        .setCall(
            CelExpr.CelCall.newBuilder().setFunction(function).addArgs(left).addArgs(right).build())
        .build();
  }
}
