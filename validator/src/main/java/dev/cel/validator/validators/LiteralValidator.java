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

package dev.cel.validator.validators;

import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.validator.CelAstValidator;
import java.util.Optional;

/**
 * LiteralValidator defines the common logic to handle simple validation of a literal in a function
 * call by evaluating it and ensuring that no errors are thrown (example: duration / timestamp
 * literals).
 */
abstract class LiteralValidator implements CelAstValidator {
  private final String functionName;
  private final Class<?> expectedResultType;

  protected LiteralValidator(String functionName, Class<?> expectedResultType) {
    this.functionName = functionName;
    this.expectedResultType = expectedResultType;
  }

  @Override
  public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
    navigableAst
        .getRoot()
        .descendants()
        .filter(
            node ->
                node.getKind().equals(Kind.CONSTANT)
                    && node.parent()
                        .map(
                            parent -> parent.expr().callOrDefault().function().equals(functionName))
                        .orElse(false))
        .map(CelNavigableExpr::expr)
        .forEach(
            expr -> {
              CelExpr callExpr =
                  CelExpr.ofCallExpr(
                      1,
                      Optional.empty(),
                      functionName,
                      ImmutableList.of(CelExpr.ofConstantExpr(2, expr.constant())));

              CelAbstractSyntaxTree ast =
                  CelAbstractSyntaxTree.newParsedAst(callExpr, CelSource.newBuilder().build());
              try {
                ast = cel.check(ast).getAst();
                Object result = cel.createProgram(ast).eval();

                if (!expectedResultType.isInstance(result)) {
                  throw new IllegalStateException(
                      String.format(
                          "Expected %s type but got %s instead",
                          expectedResultType.getName(), result.getClass().getName()));
                }

              } catch (Exception e) {
                issuesFactory.addError(
                    expr.id(),
                    String.format(
                        "%s validation failed. Reason: %s", functionName, e.getMessage()));
              }
            });
  }
}
