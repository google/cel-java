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
import com.google.protobuf.Timestamp;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.validator.CelAstValidator;
import java.util.Optional;

/** TimestampValidator ensures that timestamp literal arguments are valid. */
public class TimestampLiteralValidator implements CelAstValidator {
  public static final TimestampLiteralValidator INSTANCE = new TimestampLiteralValidator();

  @Override
  public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
    navigableAst
        .getRoot()
        .descendants()
        .filter(
            node ->
                node.getKind().equals(Kind.CONSTANT)
                    && node.parent().isPresent()
                    && node.parent().get().expr().call().function().equals("timestamp"))
        .map(CelNavigableExpr::expr)
        .forEach(
            timestampExpr -> {
              try {
                CelExpr timestampCall =
                    CelExpr.ofCallExpr(
                        1,
                        Optional.empty(),
                        "timestamp",
                        ImmutableList.of(CelExpr.ofConstantExpr(2, timestampExpr.constant())));

                CelAbstractSyntaxTree ast =
                    CelAbstractSyntaxTree.newParsedAst(
                        timestampCall, CelSource.newBuilder().build());
                ast = cel.check(ast).getAst();
                Object result = cel.createProgram(ast).eval();

                if (!(result instanceof Timestamp)) {
                  throw new IllegalStateException(
                      "Expected timestamp type but got " + result.getClass());
                }
              } catch (Exception e) {
                issuesFactory.addError(
                    timestampExpr.id(), "Timestamp validation failed. Reason: " + e.getMessage());
              }
            });
  }

  private TimestampLiteralValidator() {}
}
