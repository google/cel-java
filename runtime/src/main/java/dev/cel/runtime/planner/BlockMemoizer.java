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

import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import java.util.Arrays;

/** Handles memoization, lazy evaluation, and cycle detection for cel.@block slots. */
final class BlockMemoizer {

  private static final Object IN_PROGRESS = new Object();
  private static final Object UNSET = new Object();

  private final PlannedInterpretable[] slotExprs;
  private final Object[] slotVals;
  private final ExecutionFrame frame;

  static BlockMemoizer create(PlannedInterpretable[] slotExprs, ExecutionFrame frame) {
    return new BlockMemoizer(slotExprs, frame);
  }

  private BlockMemoizer(PlannedInterpretable[] slotExprs, ExecutionFrame frame) {
    this.slotExprs = slotExprs;
    this.frame = frame;
    this.slotVals = new Object[slotExprs.length];
    Arrays.fill(this.slotVals, UNSET);
  }

  Object resolveSlot(int idx, GlobalResolver resolver) {
    Object val = slotVals[idx];

    // Already evaluated
    if (val != UNSET && val != IN_PROGRESS) {
      if (val instanceof RuntimeException) {
        throw (RuntimeException) val;
      }
      return val;
    }

    if (val == IN_PROGRESS) {
      throw new IllegalStateException("Cycle detected: @index" + idx);
    }

    slotVals[idx] = IN_PROGRESS;
    try {
      Object result = slotExprs[idx].eval(resolver, frame);
      slotVals[idx] = result;
      return result;
    } catch (CelEvaluationException e) {
      LocalizedEvaluationException localizedException =
          new LocalizedEvaluationException(e, e.getErrorCode(), slotExprs[idx].expr().id());
      slotVals[idx] = localizedException;
      throw localizedException;
    } catch (RuntimeException e) {
      slotVals[idx] = e;
      throw e;
    }
  }
}
