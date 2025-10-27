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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.ImmutableMapValue;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalCreateMap implements CelValueInterpretable {

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] keys;

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] values;

  @Override
  public CelValue eval(GlobalResolver resolver) {
    ImmutableMap.Builder<CelValue, CelValue> builder =
        ImmutableMap.builderWithExpectedSize(keys.length);
    for (int i = 0; i < keys.length; i++) {
      builder.put(keys[i].eval(resolver), values[i].eval(resolver));
    }
    return ImmutableMapValue.create(builder.buildOrThrow());
  }

  static EvalCreateMap create(CelValueInterpretable[] keys, CelValueInterpretable[] values) {
    return new EvalCreateMap(keys, values);
  }

  private EvalCreateMap(CelValueInterpretable[] keys, CelValueInterpretable[] values) {
    Preconditions.checkArgument(keys.length == values.length);
    this.keys = keys;
    this.values = values;
  }
}
