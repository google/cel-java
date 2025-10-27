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
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueProvider;
import dev.cel.runtime.GlobalResolver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Immutable
final class EvalCreateStruct implements CelValueInterpretable {

  private final CelValueProvider valueProvider;
  private final String typeName;

  @SuppressWarnings("Immutable")
  private final String[] keys;

  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] values;

  @Override
  public CelValue eval(GlobalResolver resolver) {
    Map<String, Object> fieldValues = new HashMap<>();
    for (int i = 0; i < keys.length; i++) {
      Object value = values[i].eval(resolver).value();
      fieldValues.put(keys[i], value);
    }

    return valueProvider
        .newValue(typeName, Collections.unmodifiableMap(fieldValues))
        .orElseThrow(() -> new IllegalArgumentException("Type name not found: " + typeName));
  }

  static EvalCreateStruct create(
      CelValueProvider valueProvider,
      String typeName,
      String[] keys,
      CelValueInterpretable[] values) {
    return new EvalCreateStruct(valueProvider, typeName, keys, values);
  }

  private EvalCreateStruct(
      CelValueProvider valueProvider,
      String typeName,
      String[] keys,
      CelValueInterpretable[] values) {
    this.valueProvider = valueProvider;
    this.typeName = typeName;
    this.keys = keys;
    this.values = values;
  }
}
