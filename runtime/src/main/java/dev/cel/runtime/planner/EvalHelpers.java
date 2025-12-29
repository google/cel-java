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

import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.GlobalResolver;

final class EvalHelpers {

  static Object evalNonstrictly(
      PlannedInterpretable interpretable, GlobalResolver resolver, ExecutionFrame frame) {
    try {
      return interpretable.eval(resolver, frame);
    } catch (StrictErrorException e) {
      // Intercept the strict exception to get a more localized expr ID for error reporting purposes
      // Example: foo [1] && strict_err [2] -> ID 2 is propagated.
      return ErrorValue.create(e.exprId(), e);
    } catch (Exception e) {
      return ErrorValue.create(interpretable.exprId(), e);
    }
  }

  static Object evalStrictly(
      PlannedInterpretable interpretable, GlobalResolver resolver, ExecutionFrame frame) {
    try {
      return interpretable.eval(resolver, frame);
    } catch (CelRuntimeException e) {
      throw new StrictErrorException(e, interpretable.exprId());
    } catch (Exception e) {
      throw new StrictErrorException(
          e.getCause(), CelErrorCode.INTERNAL_ERROR, interpretable.exprId());
    }
  }

  private EvalHelpers() {}
}
