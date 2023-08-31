// Copyright 2023 Google LLC
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

package dev.cel.common.ast;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.runtime.CelEvaluationException;

/** Utility class for working with CelExpr. */
public final class CelExprUtil {

  /**
   * Type-checks and evaluates a CelExpr. This method should be used in the context of validating or
   * optimizing an AST.
   *
   * @return Evaluated result.
   * @throws CelValidationException if CelExpr fails to type-check.
   * @throws CelEvaluationException if CelExpr fails to evaluate.
   */
  @CanIgnoreReturnValue
  public static Object evaluateExpr(Cel cel, CelExpr expr)
      throws CelValidationException, CelEvaluationException {
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.newParsedAst(expr, CelSource.newBuilder().build());
    ast = cel.check(ast).getAst();

    return cel.createProgram(ast).eval();
  }

  /**
   * Type-checks and evaluates a CelExpr. The evaluated result is then checked to see if it's the
   * expected result type.
   *
   * <p>This method should be used in the context of validating or optimizing an AST.
   *
   * @return Evaluated result.
   * @throws CelValidationException if CelExpr fails to type-check.
   * @throws CelEvaluationException if CelExpr fails to evaluate.
   * @throws IllegalStateException if the evaluated result is not of type {@code
   *     expectedResultType}.
   */
  @CanIgnoreReturnValue
  public static Object evaluateExpr(Cel cel, CelExpr expr, Class<?> expectedResultType)
      throws CelValidationException, CelEvaluationException {
    Object result = evaluateExpr(cel, expr);
    if (!expectedResultType.isInstance(result)) {
      throw new IllegalStateException(
          String.format(
              "Expected %s type but got %s instead",
              expectedResultType.getName(), result.getClass().getName()));
    }
    return result;
  }

  private CelExprUtil() {}
}
