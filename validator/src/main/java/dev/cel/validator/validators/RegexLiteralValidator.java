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
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.validator.CelAstValidator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** RegexLiteralValidator ensures that regex patterns are valid. */
public final class RegexLiteralValidator implements CelAstValidator {
  public static final RegexLiteralValidator INSTANCE = new RegexLiteralValidator();

  @Override
  public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
    navigableAst
        .getRoot()
        .allNodes()
        .filter(node -> node.expr().callOrDefault().function().equals("matches"))
        .filter(node -> ImmutableList.of(1, 2).contains(node.expr().call().args().size()))
        .map(node -> node.expr().call())
        .forEach(
            matchesCallExpr -> {
              CelExpr regexArg =
                  matchesCallExpr.target().isPresent()
                      ? matchesCallExpr.args().get(0)
                      : matchesCallExpr.args().get(1);
              if (!regexArg.exprKind().getKind().equals(Kind.CONSTANT)) {
                return;
              }

              String regexPattern = regexArg.constant().stringValue();
              try {
                Pattern.compile(regexPattern);
              } catch (PatternSyntaxException e) {
                issuesFactory.addError(
                    regexArg.id(), "Regex validation failed. Reason: " + e.getMessage());
              }
            });
  }

  private RegexLiteralValidator() {}
}
