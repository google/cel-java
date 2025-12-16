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

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.StructType;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.StructValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Immutable
final class EvalCreateStruct extends PlannedInterpretable {

  private final CelValueProvider valueProvider;
  private final StructType structType;

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final String[] keys;

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final Interpretable[] values;

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    Map<String, Object> fieldValues = new HashMap<>();
    for (int i = 0; i < keys.length; i++) {
      Object value = values[i].eval(resolver);
      fieldValues.put(keys[i], value);
    }

    // Either a primitive (wrappers) or a struct is produced
    Object value =
        valueProvider
            .newValue(structType.name(), Collections.unmodifiableMap(fieldValues))
            .orElseThrow(() -> new IllegalArgumentException("Type name not found: " + structType));

    if (value instanceof StructValue) {
      return ((StructValue) value).value();
    }

    return value;
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

  static EvalCreateStruct create(
      long exprId,
      CelValueProvider valueProvider,
      StructType structType,
      String[] keys,
      Interpretable[] values) {
    return new EvalCreateStruct(exprId, valueProvider, structType, keys, values);
  }

  private EvalCreateStruct(
      long exprId,
      CelValueProvider valueProvider,
      StructType structType,
      String[] keys,
      Interpretable[] values) {
    super(exprId);
    this.valueProvider = valueProvider;
    this.structType = structType;
    this.keys = keys;
    this.values = values;
  }
}
