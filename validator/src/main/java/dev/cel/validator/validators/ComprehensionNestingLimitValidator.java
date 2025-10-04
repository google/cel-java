// Copyright 2024 Google LLC
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

/** Enforces a compiled AST to stay below the configured depth limit. */
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
        .descendants(TraversalOrder.PRE_ORDER)
        .filter(node -> node.getKind() == ExprKind.Kind.COMPREHENSION)
        .filter(node -> nestingLevel(node) > nestingLimit)
        .forEach(
            node ->
                issuesFactory.addError(
                    node.id(),
                    String.format(
                        "comprehension nesting exceeds the configured limit: %s.", nestingLimit)));
  }

  private static boolean isTrivialComprehension(CelNavigableExpr node) {
    return node.expr().comprehension().iterRange().getKind() == ExprKind.Kind.LIST
        && node.expr().comprehension().iterRange().list().elements().isEmpty();
  }

  private static int nestingLevel(CelNavigableExpr node) {
    if (isTrivialComprehension(node)) {
      return 0;
    }
    int count = 1;
    CelNavigableExpr index = node;
    while (index.parent().isPresent()) {
      CelNavigableExpr parent = index.parent().get();

      if (parent.getKind() == ExprKind.Kind.COMPREHENSION && !isTrivialComprehension(parent)) {
        // If the parent is not a comprehension, keep going up the tree.
        // If the parent has an empty list range, it has no added cost.
        // TODO: may want to consider ignoring chaining, but this is the behavior that the cel-go
        // validator uses.
        count += 1;
      }
      index = parent;
    }
    return count;
  }

  private ComprehensionNestingLimitValidator(int maxNesting) {
    this.nestingLimit = maxNesting;
  }
}
