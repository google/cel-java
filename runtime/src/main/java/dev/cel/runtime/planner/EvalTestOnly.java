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
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalTestOnly extends InterpretableAttribute {

  private final InterpretableAttribute attr;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    return attr.eval(resolver, frame);
  }

  @Override
  public EvalTestOnly addQualifier(long exprId, Qualifier qualifier) {
    PresenceTestQualifier presenceTestQualifier = PresenceTestQualifier.create(qualifier.value());
    return new EvalTestOnly(exprId(), attr.addQualifier(exprId, presenceTestQualifier));
  }

  static EvalTestOnly create(long exprId, InterpretableAttribute attr) {
    return new EvalTestOnly(exprId, attr);
  }

  private EvalTestOnly(long exprId, InterpretableAttribute attr) {
    super(exprId);
    this.attr = attr;
  }
}
