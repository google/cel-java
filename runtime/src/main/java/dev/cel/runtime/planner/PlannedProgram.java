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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.common.exceptions.CelRuntimeException;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.Activation;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationExceptionBuilder;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Program;
import java.util.Map;

@Immutable
@AutoValue
abstract class PlannedProgram implements Program {
  abstract PlannedInterpretable interpretable();

  abstract ErrorMetadata metadata();

  abstract CelOptions options();

  @Override
  public Object eval() throws CelEvaluationException {
    return evalOrThrow(interpretable(), GlobalResolver.EMPTY);
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    return evalOrThrow(interpretable(), Activation.copyOf(mapValue));
  }

  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    throw new UnsupportedOperationException("Late bound functions not supported yet");
  }

  private Object evalOrThrow(PlannedInterpretable interpretable, GlobalResolver resolver)
      throws CelEvaluationException {
    try {
      ExecutionFrame frame = ExecutionFrame.create(resolver, options());
      Object evalResult = interpretable.eval(resolver, frame);
      if (evalResult instanceof ErrorValue) {
        ErrorValue errorValue = (ErrorValue) evalResult;
        throw newCelEvaluationException(errorValue.exprId(), errorValue.value());
      }

      return evalResult;
    } catch (RuntimeException e) {
      throw newCelEvaluationException(interpretable.exprId(), e);
    }
  }

  private CelEvaluationException newCelEvaluationException(long exprId, Exception e) {
    CelEvaluationExceptionBuilder builder;
    if (e instanceof StrictErrorException) {
      // Preserve detailed error, including error codes if one exists.
      builder = CelEvaluationExceptionBuilder.newBuilder((CelRuntimeException) e.getCause());
    } else if (e instanceof CelRuntimeException) {
      builder = CelEvaluationExceptionBuilder.newBuilder((CelRuntimeException) e);
    } else {
      builder = CelEvaluationExceptionBuilder.newBuilder(e.getMessage()).setCause(e);
    }

    return builder.setMetadata(metadata(), exprId).build();
  }

  static Program create(
      PlannedInterpretable interpretable, ErrorMetadata metadata, CelOptions options) {
    return new AutoValue_PlannedProgram(interpretable, metadata, options);
  }
}
