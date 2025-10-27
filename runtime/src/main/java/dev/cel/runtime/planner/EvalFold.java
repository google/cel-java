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
import dev.cel.common.values.IntValue;
import dev.cel.runtime.GlobalResolver;
import java.util.Collection;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;

@Immutable
final class EvalFold implements CelValueInterpretable {

  private final String accuVar;
  private final CelValueInterpretable accuInit;
  private final String iterVar;
  private final String iterVar2;
  private final CelValueInterpretable iterRange;
  private final CelValueInterpretable condition;
  private final CelValueInterpretable loopStep;
  private final CelValueInterpretable result;

  @Override
  public CelValue eval(GlobalResolver resolver) {
    // TODO: Consider creating a folder abstraction like in cel-go. This requires some
    // legwork in attribute qualification.
    Collection<CelValue> foldRange = (Collection<CelValue>) iterRange.eval(resolver);

    Folder folder = new Folder(resolver, accuVar, iterVar, iterVar2);

    folder.accuVal = accuInit.eval(folder);

    long index = 0;
    for (Iterator<CelValue> iterator = foldRange.iterator(); iterator.hasNext(); ) {
      // TODO: Implement condition
      if (iterVar2.isEmpty()) {
        folder.iterVarVal = iterator.next();
      } else {
        folder.iterVarVal = IntValue.create(index);
        folder.iterVar2Val = iterator.next();
      }
      folder.accuVal = loopStep.eval(folder);
      index++;
    }

    return result.eval(folder);
  }

  private static class Folder implements GlobalResolver {
    private final GlobalResolver resolver;
    private final String accuVar;
    private final String iterVar;
    private final String iterVar2;

    private CelValue iterVarVal;
    private CelValue iterVar2Val;
    private CelValue accuVal;

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
      CelValueInterpretable accuInit,
      String iterVar,
      String iterVar2,
      CelValueInterpretable iterRange,
      CelValueInterpretable condition,
      CelValueInterpretable loopStep,
      CelValueInterpretable result) {
    return new EvalFold(
        accuVar, accuInit, iterVar, iterVar2, iterRange, condition, loopStep, result);
  }

  private EvalFold(
      String accuVar,
      CelValueInterpretable accuInit,
      String iterVar,
      String iterVar2,
      CelValueInterpretable iterRange,
      CelValueInterpretable condition,
      CelValueInterpretable loopStep,
      CelValueInterpretable result) {
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
