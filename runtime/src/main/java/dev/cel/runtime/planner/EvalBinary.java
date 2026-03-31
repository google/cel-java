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

import static dev.cel.runtime.planner.EvalHelpers.evalNonstrictly;
import static dev.cel.runtime.planner.EvalHelpers.evalStrictly;

import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.AccumulatedUnknowns;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

final class EvalBinary extends PlannedInterpretable {

  private final CelResolvedOverload resolvedOverload;
  private final PlannedInterpretable arg1;
  private final PlannedInterpretable arg2;
  private final CelValueConverter celValueConverter;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object argVal1 =
        resolvedOverload.isStrict()
            ? evalStrictly(arg1, resolver, frame)
            : evalNonstrictly(arg1, resolver, frame);
    Object argVal2 =
        resolvedOverload.isStrict()
            ? evalStrictly(arg2, resolver, frame)
            : evalNonstrictly(arg2, resolver, frame);

    AccumulatedUnknowns unknowns = AccumulatedUnknowns.maybeMerge(null, argVal1);
    unknowns = AccumulatedUnknowns.maybeMerge(unknowns, argVal2);

    if (unknowns != null) {
      return unknowns;
    }

    return EvalHelpers.dispatch(resolvedOverload, celValueConverter, argVal1, argVal2);
  }

  static EvalBinary create(
      long exprId,
      CelResolvedOverload resolvedOverload,
      PlannedInterpretable arg1,
      PlannedInterpretable arg2,
      CelValueConverter celValueConverter) {
    return new EvalBinary(exprId, resolvedOverload, arg1, arg2, celValueConverter);
  }

  private EvalBinary(
      long exprId,
      CelResolvedOverload resolvedOverload,
      PlannedInterpretable arg1,
      PlannedInterpretable arg2,
      CelValueConverter celValueConverter) {
    super(exprId);
    this.resolvedOverload = resolvedOverload;
    this.arg1 = arg1;
    this.arg2 = arg2;
    this.celValueConverter = celValueConverter;
  }
}
