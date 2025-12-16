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
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;

@Immutable
final class EvalCreateMap extends PlannedInterpretable {

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final Interpretable[] keys;

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final Interpretable[] values;

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    ImmutableMap.Builder<Object, Object> builder =
        ImmutableMap.builderWithExpectedSize(keys.length);
    for (int i = 0; i < keys.length; i++) {
      builder.put(keys[i].eval(resolver), values[i].eval(resolver));
    }
    return builder.buildOrThrow();
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
  public Object eval(
      GlobalResolver resolver,
      CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener) {
    // TODO: Implement support
    throw new UnsupportedOperationException("Not yet supported");
  }

  static EvalCreateMap create(long exprId, Interpretable[] keys, Interpretable[] values) {
    return new EvalCreateMap(exprId, keys, values);
  }

  private EvalCreateMap(long exprId, Interpretable[] keys, Interpretable[] values) {
    super(exprId);
    Preconditions.checkArgument(keys.length == values.length);
    this.keys = keys;
    this.values = values;
  }
}
