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

import static dev.cel.runtime.planner.EvalHelpers.evalNonstrictly;
import static dev.cel.runtime.planner.EvalHelpers.evalStrictly;

import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

final class EvalUnary extends PlannedInterpretable {

  private final CelResolvedOverload resolvedOverload;
  private final PlannedInterpretable arg;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object argVal =
        resolvedOverload.isStrict()
            ? evalStrictly(arg, resolver, frame)
            : evalNonstrictly(arg, resolver, frame);
    Object[] arguments = new Object[] {argVal};

    return resolvedOverload.getDefinition().apply(arguments);
  }

  static EvalUnary create(
      long exprId, CelResolvedOverload resolvedOverload, PlannedInterpretable arg) {
    return new EvalUnary(exprId, resolvedOverload, arg);
  }

  private EvalUnary(long exprId, CelResolvedOverload resolvedOverload, PlannedInterpretable arg) {
    super(exprId);
    this.resolvedOverload = resolvedOverload;
    this.arg = arg;
  }
}
