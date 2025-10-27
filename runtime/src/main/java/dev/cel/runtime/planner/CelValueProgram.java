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
import dev.cel.common.CelRuntimeException;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
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
abstract class CelValueProgram implements Program {
  abstract CelValueInterpretable interpretable();

  abstract CelValueConverter celValueConverter();

  @Override
  public Object eval() throws CelEvaluationException {
    return evalThenUnwrap(interpretable(), GlobalResolver.EMPTY);
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    return evalThenUnwrap(interpretable(), Activation.copyOf(mapValue));
  }

  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    throw new UnsupportedOperationException("Late bound functions not supported yet");
  }

  private Object evalThenUnwrap(CelValueInterpretable interpretable, GlobalResolver resolver)
      throws CelEvaluationException {
    try {
      CelValue evalResult = interpretable.eval(resolver);
      if (evalResult instanceof ErrorValue) {
        ErrorValue errorValue = (ErrorValue) evalResult;
        throw newCelEvaluationException(errorValue.value());
      }
      return celValueConverter().fromCelValueToJavaObject(evalResult);
    } catch (CelRuntimeException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(e).build();
    } catch (RuntimeException e) {
      throw newCelEvaluationException(e);
    }
  }

  private static CelEvaluationException newCelEvaluationException(Exception e) {
    CelEvaluationExceptionBuilder builder;
    if (e instanceof CelRuntimeException) {
      builder = CelEvaluationExceptionBuilder.newBuilder((CelRuntimeException) e);
    } else {
      builder = CelEvaluationExceptionBuilder.newBuilder(e.getMessage()).setCause(e);
    }

    return builder.build();
  }

  static Program create(CelValueInterpretable interpretable, CelValueConverter celValueConverter) {
    return new AutoValue_CelValueProgram(interpretable, celValueConverter);
  }
}
