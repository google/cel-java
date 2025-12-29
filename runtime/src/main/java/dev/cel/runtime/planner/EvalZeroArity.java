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

import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

final class EvalZeroArity extends PlannedInterpretable {
  private static final Object[] EMPTY_ARRAY = new Object[0];

  private final CelResolvedOverload resolvedOverload;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    return resolvedOverload.getDefinition().apply(EMPTY_ARRAY);
  }

  static EvalZeroArity create(long exprId, CelResolvedOverload resolvedOverload) {
    return new EvalZeroArity(exprId, resolvedOverload);
  }

  private EvalZeroArity(long exprId, CelResolvedOverload resolvedOverload) {
    super(exprId);
    this.resolvedOverload = resolvedOverload;
  }
}
