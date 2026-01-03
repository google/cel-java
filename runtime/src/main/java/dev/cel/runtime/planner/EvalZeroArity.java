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

import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

final class EvalZeroArity extends PlannedInterpretable {
  private static final Object[] EMPTY_ARRAY = new Object[0];

  private final CelResolvedOverload resolvedOverload;
  private final CelValueConverter celValueConverter;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object result = resolvedOverload.getDefinition().apply(EMPTY_ARRAY);
    Object runtimeValue = celValueConverter.toRuntimeValue(result);
    if (runtimeValue instanceof CelValue) {
      return celValueConverter.unwrap((CelValue) runtimeValue);
    }
    return runtimeValue;
  }

  static EvalZeroArity create(
      long exprId, CelResolvedOverload resolvedOverload, CelValueConverter celValueConverter) {
    return new EvalZeroArity(exprId, resolvedOverload, celValueConverter);
  }

  private EvalZeroArity(
      long exprId, CelResolvedOverload resolvedOverload, CelValueConverter celValueConverter) {
    super(exprId);
    this.resolvedOverload = resolvedOverload;
    this.celValueConverter = celValueConverter;
  }
}
