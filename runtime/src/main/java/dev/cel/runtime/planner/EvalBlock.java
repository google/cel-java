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

package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.ast.CelExpr;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;

/** Eval implementation of {@code cel.@block}. */
@Immutable
final class EvalBlock extends PlannedInterpretable {

  @SuppressWarnings("Immutable") // Array not mutated after creation
  private final PlannedInterpretable[] slotExprs;

  private final PlannedInterpretable resultExpr;

  static EvalBlock create(
      CelExpr expr, PlannedInterpretable[] slotExprs, PlannedInterpretable resultExpr) {
    return new EvalBlock(expr, slotExprs, resultExpr);
  }

  private EvalBlock(
      CelExpr expr, PlannedInterpretable[] slotExprs, PlannedInterpretable resultExpr) {
    super(expr);
    this.slotExprs = slotExprs;
    this.resultExpr = resultExpr;
  }

  @Override
  Object evalInternal(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    BlockMemoizer memoizer = BlockMemoizer.create(slotExprs, frame);
    frame.setBlockMemoizer(memoizer);
    return resultExpr.eval(resolver, frame);
  }

  @Immutable
  static final class EvalBlockSlot extends PlannedInterpretable {
    private final int slotIndex;

    static EvalBlockSlot create(CelExpr expr, int slotIndex) {
      return new EvalBlockSlot(expr, slotIndex);
    }

    private EvalBlockSlot(CelExpr expr, int slotIndex) {
      super(expr);
      this.slotIndex = slotIndex;
    }

    @Override
    Object evalInternal(GlobalResolver resolver, ExecutionFrame frame) {
      return frame.getBlockMemoizer().resolveSlot(slotIndex, resolver);
    }
  }
}
