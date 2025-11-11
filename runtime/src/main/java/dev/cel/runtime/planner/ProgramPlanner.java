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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelType;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationExceptionBuilder;
import dev.cel.runtime.Interpretable;
import dev.cel.runtime.Program;

/**
 * {@code ProgramPlanner} resolves functions, types, and identifiers at plan time given a
 * parsed-only or a type-checked expression.
 */
@ThreadSafe
@Internal
public final class ProgramPlanner {

  /**
   * Plans a {@link Program} from the provided parsed-only or type-checked {@link
   * CelAbstractSyntaxTree}.
   */
  public Program plan(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    Interpretable plannedInterpretable;
    try {
      plannedInterpretable = plan(ast.getExpr(), PlannerContext.create(ast));
    } catch (RuntimeException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(e.getMessage()).setCause(e).build();
    }

    return PlannedProgram.create(plannedInterpretable);
  }

  private Interpretable plan(CelExpr celExpr, PlannerContext unused) {
    switch (celExpr.getKind()) {
      case CONSTANT:
        return planConstant(celExpr.constant());
      case NOT_SET:
        throw new UnsupportedOperationException("Unsupported kind: " + celExpr.getKind());
      default:
        throw new IllegalArgumentException("Not yet implemented kind: " + celExpr.getKind());
    }
  }

  private Interpretable planConstant(CelConstant celConstant) {
    switch (celConstant.getKind()) {
      case NULL_VALUE:
        return EvalConstant.create(celConstant.nullValue());
      case BOOLEAN_VALUE:
        return EvalConstant.create(celConstant.booleanValue());
      case INT64_VALUE:
        return EvalConstant.create(celConstant.int64Value());
      case UINT64_VALUE:
        return EvalConstant.create(celConstant.uint64Value());
      case DOUBLE_VALUE:
        return EvalConstant.create(celConstant.doubleValue());
      case STRING_VALUE:
        return EvalConstant.create(celConstant.stringValue());
      case BYTES_VALUE:
        return EvalConstant.create(celConstant.bytesValue());
      default:
        throw new IllegalStateException("Unsupported kind: " + celConstant.getKind());
    }
  }

  @AutoValue
  abstract static class PlannerContext {

    abstract ImmutableMap<Long, CelReference> referenceMap();

    abstract ImmutableMap<Long, CelType> typeMap();

    private static PlannerContext create(CelAbstractSyntaxTree ast) {
      return new AutoValue_ProgramPlanner_PlannerContext(ast.getReferenceMap(), ast.getTypeMap());
    }
  }

  public static ProgramPlanner newPlanner() {
    return new ProgramPlanner();
  }

  private ProgramPlanner() {}
}
