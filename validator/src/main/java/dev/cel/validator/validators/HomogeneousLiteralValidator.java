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
import com.google.common.collect.ImmutableSet;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import dev.cel.validator.CelAstValidator;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

/**
 * HomogeneousLiteralValidator checks that all list and map literals entries have the same types,
 * i.e. no mixed list element types or mixed map key or map value types.
 */
public final class HomogeneousLiteralValidator implements CelAstValidator {
  private final ImmutableSet<String> exemptFunctions;

  /**
   * Construct a new instance of {@link HomogeneousLiteralValidator}. This validator will not for
   * functions in {@code exemptFunctions}.
   */
  public static HomogeneousLiteralValidator newInstance(Iterable<String> exemptFunctions) {
    return new HomogeneousLiteralValidator(exemptFunctions);
  }

  /**
   * Construct a new instance of {@link HomogeneousLiteralValidator}. This validator will not for
   * functions in {@code exemptFunctions}.
   */
  public static HomogeneousLiteralValidator newInstance(String... exemptFunctions) {
    return newInstance(Arrays.asList(exemptFunctions));
  }

  @Override
  public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
    navigableAst
        .getRoot()
        .allNodes()
        .filter(
            node ->
                node.getKind().equals(Kind.CREATE_LIST) || node.getKind().equals(Kind.CREATE_MAP))
        .filter(node -> !isExemptFunction(node))
        .map(CelNavigableExpr::expr)
        .forEach(
            expr -> {
              if (expr.exprKind().getKind().equals(Kind.CREATE_LIST)) {
                validateList(navigableAst.getAst(), issuesFactory, expr);
              } else if (expr.exprKind().getKind().equals(Kind.CREATE_MAP)) {
                validateMap(navigableAst.getAst(), issuesFactory, expr);
              }
            });
  }

  private void validateList(CelAbstractSyntaxTree ast, IssuesFactory issuesFactory, CelExpr expr) {
    CelType previousType = null;
    HashSet<Integer> optionalIndices = new HashSet<>(expr.createList().optionalIndices());
    ImmutableList<CelExpr> elements = expr.createList().elements();
    for (int i = 0; i < elements.size(); i++) {
      CelExpr element = elements.get(i);
      CelType currentType = ast.getType(element.id()).get();
      if (optionalIndices.contains(i)) {
        currentType = currentType.parameters().get(0);
      }

      if (previousType == null) {
        previousType = currentType;
        continue;
      }

      reportErrorIfUnassignable(issuesFactory, element.id(), previousType, currentType);
    }
  }

  private void validateMap(CelAbstractSyntaxTree ast, IssuesFactory issuesFactory, CelExpr expr) {
    CelType previousKeyType = null;
    CelType previousValueType = null;
    for (CelCreateMap.Entry entry : expr.createMap().entries()) {
      CelType currentKeyType = ast.getType(entry.key().id()).get();
      CelType currentValueType = ast.getType(entry.value().id()).get();
      if (entry.optionalEntry()) {
        currentValueType = currentValueType.parameters().get(0);
      }

      if (previousKeyType == null) {
        previousKeyType = currentKeyType;
        previousValueType = currentValueType;
        continue;
      }

      reportErrorIfUnassignable(issuesFactory, entry.id(), previousKeyType, currentKeyType);
      reportErrorIfUnassignable(issuesFactory, entry.id(), previousValueType, currentValueType);
    }
  }

  private void reportErrorIfUnassignable(
      IssuesFactory issuesFactory, long elementId, CelType previousType, CelType currentType) {
    if (!previousType.isAssignableFrom(currentType)) {
      issuesFactory.addError(
          elementId,
          String.format(
              "expected type '%s' but found '%s'",
              CelTypes.format(previousType), CelTypes.format(currentType)));
    }
  }

  private boolean isExemptFunction(CelNavigableExpr listExpr) {
    Optional<CelNavigableExpr> parent = listExpr.parent();
    while (parent.isPresent()) {
      CelNavigableExpr node = parent.get();
      if (node.getKind().equals(Kind.CALL)) {
        if (exemptFunctions.contains(node.expr().callOrDefault().function())) {
          return true;
        }
      }

      parent = node.parent();
    }

    return false;
  }

  private HomogeneousLiteralValidator(Iterable<String> exemptFunctions) {
    this.exemptFunctions = ImmutableSet.copyOf(exemptFunctions);
  }
}
