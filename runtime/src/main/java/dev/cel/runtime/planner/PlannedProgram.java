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
import dev.cel.runtime.Activation;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationExceptionBuilder;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;
import dev.cel.runtime.Program;
import java.util.Map;

@Immutable
@AutoValue
abstract class PlannedProgram implements Program {
  abstract Interpretable interpretable();

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

  private Object evalOrThrow(Interpretable interpretable, GlobalResolver resolver)
      throws CelEvaluationException {
    try {
      return interpretable.eval(resolver);
    } catch (RuntimeException e) {
      throw newCelEvaluationException(e);
    }
  }

  private static CelEvaluationException newCelEvaluationException(Exception e) {
    return CelEvaluationExceptionBuilder.newBuilder(e.getMessage()).setCause(e).build();
  }

  static Program create(Interpretable interpretable) {
    return new AutoValue_PlannedProgram(interpretable);
  }
}
