// Copyright 2025 Google LLC
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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.ConcatenatedListView;
import dev.cel.runtime.GlobalResolver;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@Immutable
final class EvalFold extends PlannedInterpretable {

  private final String accuVar;
  private final PlannedInterpretable accuInit;
  private final String iterVar;
  private final String iterVar2;
  private final PlannedInterpretable iterRange;
  private final PlannedInterpretable condition;
  private final PlannedInterpretable loopStep;
  private final PlannedInterpretable result;

  static EvalFold create(
      long exprId,
      String accuVar,
      PlannedInterpretable accuInit,
      String iterVar,
      String iterVar2,
      PlannedInterpretable iterRange,
      PlannedInterpretable loopCondition,
      PlannedInterpretable loopStep,
      PlannedInterpretable result) {
    return new EvalFold(
        exprId, accuVar, accuInit, iterVar, iterVar2, iterRange, loopCondition, loopStep, result);
  }

  private EvalFold(
      long exprId,
      String accuVar,
      PlannedInterpretable accuInit,
      String iterVar,
      String iterVar2,
      PlannedInterpretable iterRange,
      PlannedInterpretable condition,
      PlannedInterpretable loopStep,
      PlannedInterpretable result) {
    super(exprId);
    this.accuVar = accuVar;
    this.accuInit = accuInit;
    this.iterVar = iterVar;
    this.iterVar2 = iterVar2;
    this.iterRange = iterRange;
    this.condition = condition;
    this.loopStep = loopStep;
    this.result = result;
  }

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object iterRangeRaw = iterRange.eval(resolver, frame);
    Folder folder = new Folder(resolver, accuVar, iterVar, iterVar2);
    folder.accuVal = maybeWrapAccumulator(accuInit.eval(folder, frame));

    Object result;
    if (iterRangeRaw instanceof Map) {
      result = evalMap((Map<?, ?>) iterRangeRaw, folder, frame);
    } else if (iterRangeRaw instanceof Collection) {
      result = evalList((Collection<?>) iterRangeRaw, folder, frame);
    } else {
      throw new IllegalArgumentException("Unexpected iter_range type: " + iterRangeRaw.getClass());
    }

    return maybeUnwrapAccumulator(result);
  }

  private Object evalMap(Map<?, ?> iterRange, Folder folder, ExecutionFrame frame)
      throws CelEvaluationException {
    for (Map.Entry<?, ?> entry : iterRange.entrySet()) {
      frame.incrementIterations();

      folder.iterVarVal = entry.getKey();
      if (!iterVar2.isEmpty()) {
        folder.iterVar2Val = entry.getValue();
      }

      boolean cond = (boolean) condition.eval(folder, frame);
      if (!cond) {
        return result.eval(folder, frame);
      }

      folder.accuVal = loopStep.eval(folder, frame);
    }
    return result.eval(folder, frame);
  }

  private Object evalList(Collection<?> iterRange, Folder folder, ExecutionFrame frame)
      throws CelEvaluationException {
    int index = 0;
    for (Object item : iterRange) {
      frame.incrementIterations();

      if (iterVar2.isEmpty()) {
        folder.iterVarVal = item;
      } else {
        folder.iterVarVal = (long) index;
        folder.iterVar2Val = item;
      }

      boolean cond = (boolean) condition.eval(folder, frame);
      if (!cond) {
        return result.eval(folder, frame);
      }

      folder.accuVal = loopStep.eval(folder, frame);
      index++;
    }
    return result.eval(folder, frame);
  }

  private static Object maybeWrapAccumulator(Object val) {
    if (val instanceof Collection) {
      return new ConcatenatedListView<>((Collection<?>) val);
    }
    // TODO: Introduce mutable map support (for comp v2)
    return val;
  }

  private static Object maybeUnwrapAccumulator(Object val) {
    if (val instanceof ConcatenatedListView) {
      return ImmutableList.copyOf((ConcatenatedListView<?>) val);
    }

    // TODO: Introduce mutable map support (for comp v2)
    return val;
  }

  private static class Folder implements ActivationWrapper {
    private final GlobalResolver resolver;
    private final String accuVar;
    private final String iterVar;
    private final String iterVar2;

    private Object iterVarVal;
    private Object iterVar2Val;
    private Object accuVal;

    private Folder(GlobalResolver resolver, String accuVar, String iterVar, String iterVar2) {
      this.resolver = resolver;
      this.accuVar = accuVar;
      this.iterVar = iterVar;
      this.iterVar2 = iterVar2;
    }

    @Override
    public GlobalResolver unwrap() {
      return resolver;
    }

    @Override
    public @Nullable Object resolve(String name) {
      if (name.equals(accuVar)) {
        return accuVal;
      }

      if (name.equals(iterVar)) {
        return this.iterVarVal;
      }

      if (name.equals(iterVar2)) {
        return this.iterVar2Val;
      }

      return resolver.resolve(name);
    }
  }
}
