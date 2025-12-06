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

import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import java.util.Collection;
import java.util.Iterator;
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

  private static Collection<Object> adaptIterRange(Object iterRangeRaw) {
    // TODO: Adapt to mutable list/map. At the moment, this is O(n^2) for certain
    // operations like filter.
    if (iterRangeRaw instanceof Collection) {
      return (Collection<Object>) iterRangeRaw;
    } else if (iterRangeRaw instanceof Map) {
      return ((Map<Object, Object>) iterRangeRaw).keySet();
    }

    throw new IllegalArgumentException("Unexpected iter_range type: " + iterRangeRaw.getClass());
  }

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    Collection<Object> foldRange = adaptIterRange(iterRange.eval(resolver));

    Folder folder = new Folder(resolver, accuVar, iterVar, iterVar2);

    folder.accuVal = accuInit.eval(folder);

    long index = 0;
    for (Iterator<Object> iterator = foldRange.iterator(); iterator.hasNext(); ) {
      boolean cond = (boolean) condition.eval(folder);
      if (!cond) {
        return result.eval(folder);
      }
      if (iterVar2.isEmpty()) {
        folder.iterVarVal = iterator.next();
      } else {
        folder.iterVarVal = index;
        folder.iterVar2Val = iterator.next();
      }

      // TODO: Introduce comprehension safety controls, such as iteration limit.
      folder.accuVal = loopStep.eval(folder);
      index++;
    }

    return result.eval(folder);
  }

  @Override
  public Object eval(GlobalResolver resolver, CelEvaluationListener listener)
      throws CelEvaluationException {
    return null;
  }

  @Override
  public Object eval(GlobalResolver resolver, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    return null;
  }

  @Override
  public Object eval(
      GlobalResolver resolver,
      CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener)
      throws CelEvaluationException {
    return null;
  }

  private static class Folder implements GlobalResolver {
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

  static EvalFold create(
      long exprId,
      String accuVar,
      PlannedInterpretable accuInit,
      String iterVar,
      String iterVar2,
      PlannedInterpretable iterRange,
      PlannedInterpretable condition,
      PlannedInterpretable loopStep,
      PlannedInterpretable result) {
    return new EvalFold(
        exprId, accuVar, accuInit, iterVar, iterVar2, iterRange, condition, loopStep, result);
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
}
