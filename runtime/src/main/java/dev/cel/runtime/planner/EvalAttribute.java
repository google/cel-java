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
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalAttribute extends PlannedInterpretable {

  private final Attribute attr;

  @Override
  public Object eval(GlobalResolver resolver) {
    return attr.resolve(resolver);
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
    throw new UnsupportedOperationException("Not yet supported");
  }

  static EvalAttribute create(long exprId, Attribute attr) {
    return new EvalAttribute(exprId, attr);
  }

  private EvalAttribute(long exprId, Attribute attr) {
    super(exprId);
    this.attr = attr;
  }
}
