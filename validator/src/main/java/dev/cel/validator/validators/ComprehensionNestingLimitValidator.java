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

package dev.cel.validator.validators;

import static com.google.common.base.Preconditions.checkArgument;

import dev.cel.bundle.Cel;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.TraversalOrder;
import dev.cel.validator.CelAstValidator;

/**
 * Checks that the nesting depth of comprehensions does not exceed the configured limit. Nesting
 * occurs when a comprehension is used in the range expression or the body of a comprehension.
 *
 * <p>Trivial comprehensions (comprehensions over an empty range) do not count towards the limit.
 */
public final class ComprehensionNestingLimitValidator implements CelAstValidator {
  private final int nestingLimit;

  /**
   * Constructs a new instance of {@link ComprehensionNestingLimitValidator} with the configured
   * maxNesting as its limit. A limit of 0 means no comprehensions are allowed.
   */
  public static ComprehensionNestingLimitValidator newInstance(int maxNesting) {
    checkArgument(maxNesting >= 0);
    return new ComprehensionNestingLimitValidator(maxNesting);
  }

  @Override
  public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
    navigableAst
        .getRoot()
        .allNodes(TraversalOrder.PRE_ORDER)
        .filter(node -> node.getKind().equals(ExprKind.Kind.COMPREHENSION))
        .filter(node -> nestingLevel(node) > nestingLimit)
        .forEach(
            node ->
                issuesFactory.addError(
                    node.id(),
                    String.format(
                        "comprehension nesting exceeds the configured limit: %s.", nestingLimit)));
  }

  private static boolean isTrivialComprehension(CelNavigableExpr node) {
    return (node.expr().comprehension().iterRange().getKind().equals(ExprKind.Kind.LIST)
            && node.expr().comprehension().iterRange().list().elements().isEmpty())
        || (node.expr().comprehension().iterRange().getKind().equals(ExprKind.Kind.MAP)
            && node.expr().comprehension().iterRange().map().entries().isEmpty());
  }

  private static int nestingLevel(CelNavigableExpr node) {
    if (isTrivialComprehension(node)) {
      return 0;
    }
    int count = 1;
    while (node.parent().isPresent()) {
      CelNavigableExpr parent = node.parent().get();

      if (parent.getKind().equals(ExprKind.Kind.COMPREHENSION) && !isTrivialComprehension(parent)) {
        count++;
      }
      node = parent;
    }
    return count;
  }

  private ComprehensionNestingLimitValidator(int maxNesting) {
    this.nestingLimit = maxNesting;
  }
}
