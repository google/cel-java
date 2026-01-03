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

import static dev.cel.runtime.planner.EvalHelpers.evalStrictly;

import com.google.common.collect.ImmutableList;
import dev.cel.common.exceptions.CelOverloadNotFoundException;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

final class EvalLateBoundCall extends PlannedInterpretable {

  private final String functionName;
  private final ImmutableList<String> overloadIds;

  @SuppressWarnings("Immutable")
  private final PlannedInterpretable[] args;

  private final CelValueConverter celValueConverter;

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
            .orElseThrow(() -> new CelOverloadNotFoundException(functionName, overloadIds));

    Object result = resolvedOverload.getDefinition().apply(argVals);
    Object runtimeValue = celValueConverter.toRuntimeValue(result);
    if (runtimeValue instanceof CelValue) {
      return celValueConverter.unwrap((CelValue) runtimeValue);
    }
    return runtimeValue;
  }

  static EvalLateBoundCall create(
      long exprId,
      String functionName,
      ImmutableList<String> overloadIds,
      PlannedInterpretable[] args,
      CelValueConverter celValueConverter) {
    return new EvalLateBoundCall(exprId, functionName, overloadIds, args, celValueConverter);
  }

  private EvalLateBoundCall(
      long exprId,
      String functionName,
      ImmutableList<String> overloadIds,
      PlannedInterpretable[] args,
      CelValueConverter celValueConverter) {
    super(exprId);
    this.functionName = functionName;
    this.overloadIds = overloadIds;
    this.args = args;
    this.celValueConverter = celValueConverter;
  }
}
