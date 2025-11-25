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
import dev.cel.common.values.CelValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;
import java.util.Collection;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;

@Immutable
final class EvalFold implements Interpretable {

  private final String accuVar;
  private final Interpretable accuInit;
  private final String iterVar;
  private final String iterVar2;
  private final Interpretable iterRange;
  private final Interpretable condition;
  private final Interpretable loopStep;
  private final Interpretable result;

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    // TODO: Consider creating a folder abstraction like in cel-go. This requires some
    // legwork in attribute qualification.
    Collection<CelValue> foldRange = (Collection<CelValue>) iterRange.eval(resolver);

    Folder folder = new Folder(resolver, accuVar, iterVar, iterVar2);

    folder.accuVal = accuInit.eval(folder);

    long index = 0;
    for (Iterator<CelValue> iterator = foldRange.iterator(); iterator.hasNext(); ) {
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

      // Todo: !f.computeResult check
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
      String accuVar,
      Interpretable accuInit,
      String iterVar,
      String iterVar2,
      Interpretable iterRange,
      Interpretable condition,
      Interpretable loopStep,
      Interpretable result) {
    return new EvalFold(
        accuVar, accuInit, iterVar, iterVar2, iterRange, condition, loopStep, result);
  }

  private EvalFold(
      String accuVar,
      Interpretable accuInit,
      String iterVar,
      String iterVar2,
      Interpretable iterRange,
      Interpretable condition,
      Interpretable loopStep,
      Interpretable result) {
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
