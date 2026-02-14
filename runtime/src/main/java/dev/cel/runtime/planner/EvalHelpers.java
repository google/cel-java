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

import com.google.common.base.Joiner;
import dev.cel.common.CelErrorCode;
import dev.cel.common.exceptions.CelRuntimeException;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

final class EvalHelpers {

  static Object evalNonstrictly(
      PlannedInterpretable interpretable, GlobalResolver resolver, ExecutionFrame frame) {
    try {
      return interpretable.eval(resolver, frame);
    } catch (LocalizedEvaluationException e) {
      // Intercept the localized exception to get a more specific expr ID for error reporting
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
    } catch (LocalizedEvaluationException e) {
      // Already localized - propagate as-is to preserve inner expression ID
      throw e;
    } catch (CelRuntimeException e) {
      // Wrap with current interpretable's location
      throw new LocalizedEvaluationException(e, interpretable.exprId());
    } catch (Exception e) {
      // Wrap generic exceptions with location
      throw new LocalizedEvaluationException(
          e, CelErrorCode.INTERNAL_ERROR, interpretable.exprId());
    }
  }

  static Object dispatch(CelResolvedOverload overload, CelValueConverter valueConverter, Object[] args) throws CelEvaluationException {
    try {
      Object result = overload.getDefinition().apply(args);
      Object runtimeValue = valueConverter.toRuntimeValue(result);
      if (runtimeValue instanceof CelValue) {
        return valueConverter.unwrap((CelValue) runtimeValue);
      }

      return runtimeValue;
    } catch (CelRuntimeException e) {
      // Function dispatch failure that's already been handled -- just propagate.
      throw e;
    } catch (RuntimeException e) {
      // Unexpected function dispatch failure.
      throw new IllegalArgumentException(String.format(
          "Function '%s' failed with arg(s) '%s'",
          overload.getOverloadId(), Joiner.on(", ").join(args)),
          e);
    }
  }

  private EvalHelpers() {}
}
