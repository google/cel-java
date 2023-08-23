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
import com.google.protobuf.Duration;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.validator.CelAstValidator;
import java.util.Optional;

/** DurationLiteralValidator ensures that duration literal arguments are valid. */
public class DurationLiteralValidator implements CelAstValidator {
  private static final String DURATION_FUNCTION = "duration";

  public static final DurationLiteralValidator INSTANCE = new DurationLiteralValidator();

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
                            parent ->
                                parent.expr().callOrDefault().function().equals(DURATION_FUNCTION))
                        .orElse(false))
        .map(CelNavigableExpr::expr)
        .forEach(
            durationExpr -> {
              CelExpr durationCall =
                  CelExpr.ofCallExpr(
                      1,
                      Optional.empty(),
                      DURATION_FUNCTION,
                      ImmutableList.of(CelExpr.ofConstantExpr(2, durationExpr.constant())));

              CelAbstractSyntaxTree ast =
                  CelAbstractSyntaxTree.newParsedAst(durationCall, CelSource.newBuilder().build());
              try {
                ast = cel.check(ast).getAst();
                Object result = cel.createProgram(ast).eval();

                if (!(result instanceof Duration)) {
                  throw new IllegalStateException(
                      "Expected duration type but got " + result.getClass());
                }
              } catch (Exception e) {
                issuesFactory.addError(
                    durationExpr.id(), "Duration validation failed. Reason: " + e.getMessage());
              }
            });
  }

  private DurationLiteralValidator() {}
}
