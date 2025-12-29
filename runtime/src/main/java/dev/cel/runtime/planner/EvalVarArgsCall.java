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

@SuppressWarnings("Immutable")
final class EvalVarArgsCall extends PlannedInterpretable {

  private final CelResolvedOverload resolvedOverload;
  private final PlannedInterpretable[] args;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object[] argVals = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      PlannedInterpretable arg = args[i];
      argVals[i] =
          resolvedOverload.isStrict()
              ? evalStrictly(arg, resolver, frame)
              : evalNonstrictly(arg, resolver, frame);
    }

    return resolvedOverload.getDefinition().apply(argVals);
  }

  static EvalVarArgsCall create(
      long exprId, CelResolvedOverload resolvedOverload, PlannedInterpretable[] args) {
    return new EvalVarArgsCall(exprId, resolvedOverload, args);
  }

  private EvalVarArgsCall(
      long exprId, CelResolvedOverload resolvedOverload, PlannedInterpretable[] args) {
    super(exprId);
    this.resolvedOverload = resolvedOverload;
    this.args = args;
  }
}
