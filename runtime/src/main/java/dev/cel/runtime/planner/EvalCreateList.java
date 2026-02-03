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
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalCreateList extends PlannedInterpretable {

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final PlannedInterpretable[] values;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(values.length);
    for (PlannedInterpretable value : values) {
      builder.add(EvalHelpers.evalStrictly(value, resolver, frame));
    }
    return builder.build();
  }

  static EvalCreateList create(long exprId, PlannedInterpretable[] values) {
    return new EvalCreateList(exprId, values);
  }

  private EvalCreateList(long exprId, PlannedInterpretable[] values) {
    super(exprId);
    this.values = values;
  }
}
