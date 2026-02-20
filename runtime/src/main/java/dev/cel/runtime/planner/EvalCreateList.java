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
import java.util.Optional;

@Immutable
final class EvalCreateList extends PlannedInterpretable {

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final PlannedInterpretable[] values;

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final int[] optionalIndices;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(values.length);
    for (int i = 0; i < values.length; i++) {
      Object element = EvalHelpers.evalStrictly(values[i], resolver, frame);
      if (isOptionalIndex(i)) {
        if (element instanceof Optional) {
          Optional<?> opt = (Optional<?>) element;
          opt.ifPresent(builder::add);
        }
      } else {
        builder.add(element);
      }
    }
    return builder.build();
  }

  private boolean isOptionalIndex(int index) {
    for (int optionalIndex : optionalIndices) {
      if (optionalIndex == index) {
        return true;
      }
    }

    return false;
  }

  static EvalCreateList create(long exprId, PlannedInterpretable[] values, int[] optionalIndices) {
    return new EvalCreateList(exprId, values, optionalIndices);
  }

  private EvalCreateList(long exprId, PlannedInterpretable[] values, int[] optionalIndices) {
    super(exprId);
    this.values = values;
    this.optionalIndices = optionalIndices;
  }
}
