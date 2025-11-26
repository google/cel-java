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

import com.google.common.base.Preconditions;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;

final class EvalAnd implements Interpretable {

  @SuppressWarnings("Immutable")
  private final Interpretable[] args;

  @Override
  public Object eval(GlobalResolver resolver) {
    ErrorValue errorValue = null;
    for (Interpretable arg : args) {
      Object argVal = evalNonstrictly(arg, resolver);
      if (argVal instanceof Boolean) {
        // Short-circuit on false
        if (!((boolean) argVal)) {
          return false;
        }
      } else if (argVal instanceof ErrorValue) {
        errorValue = (ErrorValue) argVal;
      } else {
        // TODO: Handle unknowns
        throw new IllegalArgumentException(
            String.format("Expected boolean value, found: %s", argVal));
      }
    }

    if (errorValue != null) {
      return errorValue;
    }

    return true;
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

  static EvalAnd create(Interpretable[] args) {
    return new EvalAnd(args);
  }

  private EvalAnd(Interpretable[] args) {
    Preconditions.checkArgument(args.length == 2);
    this.args = args;
  }
}
