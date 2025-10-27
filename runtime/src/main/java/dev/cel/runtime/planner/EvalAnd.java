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
import dev.cel.common.values.BoolValue;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.GlobalResolver;

final class EvalAnd implements CelValueInterpretable {

  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] args;

  @Override
  public CelValue eval(GlobalResolver resolver) {
    ErrorValue errorValue = null;
    for (CelValueInterpretable arg : args) {
      CelValue argVal = evalNonstrictly(arg, resolver);
      if (argVal instanceof BoolValue) {
        // Short-circuit on false
        if (!((boolean) argVal.value())) {
          return argVal;
        }
      } else if (argVal instanceof ErrorValue) {
        errorValue = (ErrorValue) argVal;
      }
    }

    if (errorValue != null) {
      return errorValue;
    }

    return BoolValue.create(true);
  }

  static EvalAnd create(CelValueInterpretable[] args) {
    return new EvalAnd(args);
  }

  private EvalAnd(CelValueInterpretable[] args) {
    Preconditions.checkArgument(args.length == 2);
    this.args = args;
  }
}
