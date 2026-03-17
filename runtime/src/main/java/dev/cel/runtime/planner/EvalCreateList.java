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
import dev.cel.runtime.AccumulatedUnknowns;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import java.util.Optional;

@Immutable
final class EvalCreateList extends PlannedInterpretable {

  @SuppressWarnings("Immutable")
  private final PlannedInterpretable[] values;

  @SuppressWarnings("Immutable")
  private final boolean[] isOptional;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(values.length);
    AccumulatedUnknowns unknowns = null;
    for (int i = 0; i < values.length; i++) {
      Object element = EvalHelpers.evalStrictly(values[i], resolver, frame);

      if (element instanceof AccumulatedUnknowns) {
        unknowns = AccumulatedUnknowns.maybeMerge(unknowns, element);
        continue;
      }

      if (isOptional[i]) {
        if (!(element instanceof Optional)) {
          throw new IllegalArgumentException(
              String.format(
                  "Cannot initialize optional list element from non-optional value %s", element));
        }

        Optional<?> opt = (Optional<?>) element;
        if (!opt.isPresent()) {
          continue;
        }
        element = opt.get();
      }

      builder.add(element);
    }

    if (unknowns != null) {
      return unknowns;
    }

    return builder.build();
  }

  static EvalCreateList create(long exprId, PlannedInterpretable[] values, boolean[] isOptional) {
    return new EvalCreateList(exprId, values, isOptional);
  }

  private EvalCreateList(long exprId, PlannedInterpretable[] values, boolean[] isOptional) {
    super(exprId);
    this.values = values;
    this.isOptional = isOptional;
  }
}
