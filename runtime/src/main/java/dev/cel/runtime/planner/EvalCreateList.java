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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;

@Immutable
final class EvalCreateList implements Interpretable {

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final Interpretable[] values;

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(values.length);
    for (Interpretable value : values) {
      builder.add(value.eval(resolver));
    }
    return builder.build();
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
  public Object eval(GlobalResolver resolver, CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener) {
    // TODO: Implement support
    throw new UnsupportedOperationException("Not yet supported");
  }

  static EvalCreateList create(Interpretable[] values) {
    return new EvalCreateList(values);
  }

  private EvalCreateList(Interpretable[] values) {
    this.values = values;
  }
}
