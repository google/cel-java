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

import com.google.common.base.Preconditions;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;

final class EvalConditional extends PlannedInterpretable {

  @SuppressWarnings("Immutable")
  private final Interpretable[] args;

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    Interpretable condition = args[0];
    Interpretable truthy = args[1];
    Interpretable falsy = args[2];
    // TODO: Handle unknowns
    Object condResult = condition.eval(resolver);
    if (!(condResult instanceof Boolean)) {
      throw new IllegalArgumentException(
          String.format("Expected boolean value, found :%s", condResult));
    }

    // TODO: Handle exhaustive eval
    if ((boolean) condResult) {
      return truthy.eval(resolver);
    }

    return falsy.eval(resolver);
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

  static EvalConditional create(long exprId, Interpretable[] args) {
    return new EvalConditional(exprId, args);
  }

  private EvalConditional(long exprId, Interpretable[] args) {
    super(exprId);
    Preconditions.checkArgument(args.length == 3);
    this.args = args;
  }
}
