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
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalAttribute implements CelValueInterpretable {

  private final CelValueConverter celValueConverter;
  private final Attribute attr;

  @Override
  public CelValue eval(GlobalResolver resolver) {
    Object obj = attr.resolve(resolver);
    return celValueConverter.fromJavaObjectToCelValue(obj);
  }

  static EvalAttribute create(CelValueConverter celValueConverter, Attribute attr) {
    return new EvalAttribute(celValueConverter, attr);
  }

  private EvalAttribute(CelValueConverter celValueConverter, Attribute attr) {
    this.celValueConverter = celValueConverter;
    this.attr = attr;
  }
}
