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

package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;

@Immutable
@AutoValue
abstract class LiteProgramImpl implements Program {

  abstract Interpretable interpretable();

  @Override
  public Object eval() throws CelEvaluationException {
    return interpretable().eval(GlobalResolver.EMPTY);
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    return interpretable().eval(Activation.copyOf(mapValue));
  }

  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    return interpretable().eval(Activation.copyOf(mapValue), lateBoundFunctionResolver);
  }

  @Override
  public Object eval(CelVariableResolver resolver, CelFunctionResolver lateBoundFunctionResolver) {
    // TODO: Wire in program planner
    throw new UnsupportedOperationException("To be implemented");
  }

  @Override
  public Object eval(CelVariableResolver resolver) throws CelEvaluationException {
    // TODO: Wire in program planner
    throw new UnsupportedOperationException("To be implemented");
  }

  static Program plan(Interpretable interpretable) {
    return new AutoValue_LiteProgramImpl(interpretable);
  }
}
