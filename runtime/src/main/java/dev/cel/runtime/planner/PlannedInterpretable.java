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
import dev.cel.common.ast.CelExpr;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.InterpreterUtil;

@Immutable
abstract class PlannedInterpretable {
  private final CelExpr expr;

  /** Runs interpretation with the given activation which supplies name/value bindings. */
  final Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object result = evalInternal(resolver, frame);
    CelEvaluationListener listener = frame.getListener();
    if (listener != null) {
      listener.callback(expr, InterpreterUtil.maybeAdaptToCelUnknownSet(result));
    }
    return result;
  }

  abstract Object evalInternal(GlobalResolver resolver, ExecutionFrame frame)
      throws CelEvaluationException;

  CelExpr expr() {
    return expr;
  }

  PlannedInterpretable(CelExpr expr) {
    this.expr = expr;
  }
}
