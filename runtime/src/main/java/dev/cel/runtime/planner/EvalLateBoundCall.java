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

import static dev.cel.runtime.planner.EvalHelpers.evalStrictly;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

@SuppressWarnings("Immutable")
final class EvalLateBoundCall extends PlannedInterpretable {

  private final String functionName;
  private final ImmutableList<String> overloadIds;
  private final PlannedInterpretable[] args;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object[] argVals = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      PlannedInterpretable arg = args[i];
      // Late bound functions are assumed to be strict.
      argVals[i] = evalStrictly(arg, resolver, frame);
    }

    CelResolvedOverload resolvedOverload =
        frame
            .findOverload(functionName, overloadIds, argVals)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "No matching overload for function '%s'. Overload candidates: %s",
                            functionName, Joiner.on(", ").join(overloadIds))));

    return resolvedOverload.getDefinition().apply(argVals);
  }

  static EvalLateBoundCall create(
      long exprId,
      String functionName,
      ImmutableList<String> overloadIds,
      PlannedInterpretable[] args) {
    return new EvalLateBoundCall(exprId, functionName, overloadIds, args);
  }

  private EvalLateBoundCall(
      long exprId,
      String functionName,
      ImmutableList<String> overloadIds,
      PlannedInterpretable[] args) {
    super(exprId);
    this.functionName = functionName;
    this.overloadIds = overloadIds;
    this.args = args;
  }
}
