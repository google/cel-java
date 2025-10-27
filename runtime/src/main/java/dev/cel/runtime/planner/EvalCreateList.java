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
import dev.cel.common.values.CelValue;
import dev.cel.common.values.ImmutableListValue;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalCreateList implements CelValueInterpretable {

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] values;

  @Override
  public CelValue eval(GlobalResolver resolver) {
    ImmutableList.Builder<CelValue> builder = ImmutableList.builderWithExpectedSize(values.length);
    for (CelValueInterpretable value : values) {
      builder.add(value.eval(resolver));
    }
    return ImmutableListValue.create(builder.build());
  }

  static EvalCreateList create(CelValueInterpretable[] values) {
    return new EvalCreateList(values);
  }

  private EvalCreateList(CelValueInterpretable[] values) {
    this.values = values;
  }
}
