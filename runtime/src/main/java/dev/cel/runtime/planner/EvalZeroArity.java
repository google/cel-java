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
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;

final class EvalZeroArity implements Interpretable {

  private static final Object[] EMPTY_ARRAY = new Object[0];

  private final CelResolvedOverload resolvedOverload;

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    return resolvedOverload.getDefinition().apply(EMPTY_ARRAY);
  }

  @Override
  public Object eval(GlobalResolver resolver, CelEvaluationListener listener) {
    // TODO: Implement support
    throw new UnsupportedOperationException("Not yet supported");
  }

  @Override
  public Object eval(GlobalResolver resolver, CelFunctionResolver lateBoundFunctionResolver) {
    // TODO: Implement support
    throw new UnsupportedOperationException("Not yet supported");
  }

  @Override
  public Object eval(
      GlobalResolver resolver,
      CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener) {
    // TODO: Implement support
    throw new UnsupportedOperationException("Not yet supported");
  }

  static EvalZeroArity create(CelResolvedOverload resolvedOverload) {
    return new EvalZeroArity(resolvedOverload);
  }

  private EvalZeroArity(CelResolvedOverload resolvedOverload) {
    this.resolvedOverload = resolvedOverload;
  }
}
