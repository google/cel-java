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
package dev.cel.optimizer.optimizers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;

import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelExprUtil;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelEvaluationException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

/**
 * Performs optimization for inlining constant scalar and aggregate literal values within function
 * calls and select statements with their evaluated result.
 */
public final class ConstantFoldingOptimizer implements CelAstOptimizer {
  public static final ConstantFoldingOptimizer INSTANCE = new ConstantFoldingOptimizer();
  private static final int MAX_ITERATION_COUNT = 400;

  // Use optional.of and optional.none as sentinel function names for folding optional calls.
  // TODO: Leverage CelValue representation of Optionals instead when available.
  private static final String OPTIONAL_OF_FUNCTION = "optional.of";
  private static final String OPTIONAL_NONE_FUNCTION = "optional.none";
  private static final CelExpr OPTIONAL_NONE_EXPR =
      CelExpr.ofCallExpr(0, Optional.empty(), OPTIONAL_NONE_FUNCTION, ImmutableList.of());

  @Override
  public CelAbstractSyntaxTree optimize(CelNavigableAst navigableAst, Cel cel)
      throws CelOptimizationException {
    Set<CelExpr> visitedExprs = new HashSet<>();
    int iterCount = 0;
    while (true) {
      iterCount++;
      if (iterCount == MAX_ITERATION_COUNT) {
        throw new IllegalStateException("Max iteration count reached.");
      }
      Optional<CelExpr> foldableExpr =
          navigableAst
              .getRoot()
              .allNodes()
              .filter(ConstantFoldingOptimizer::canFold)
              .map(CelNavigableExpr::expr)
              .filter(expr -> !visitedExprs.contains(expr))
              .findAny();
      if (!foldableExpr.isPresent()) {
        break;
      }
      visitedExprs.add(foldableExpr.get());

      Optional<CelAbstractSyntaxTree> mutatedAst;
      // Attempt to prune if it is a non-strict call
      mutatedAst = maybePruneBranches(navigableAst.getAst(), foldableExpr.get());
      if (!mutatedAst.isPresent()) {
        // Evaluate the call then fold
        mutatedAst = maybeFold(cel, navigableAst.getAst(), foldableExpr.get());
      }

      if (!mutatedAst.isPresent()) {
        // Skip this expr. It's neither prune-able nor foldable.
        continue;
      }

      visitedExprs.clear();
      navigableAst = CelNavigableAst.fromAst(mutatedAst.get());
    }

    // If the output is a list, map, or struct which contains optional entries, then prune it
    // to make sure that the optionals, if resolved, do not surface in the output literal.
    navigableAst = CelNavigableAst.fromAst(pruneOptionalElements(navigableAst));

    return navigableAst.getAst();
  }

  private static boolean canFold(CelNavigableExpr navigableExpr) {
    switch (navigableExpr.getKind()) {
      case CALL:
        CelCall celCall = navigableExpr.expr().call();
        String functionName = celCall.function();

        // These are already folded or do not need to be folded.
        if (functionName.equals(OPTIONAL_OF_FUNCTION)
            || functionName.equals(OPTIONAL_NONE_FUNCTION)) {
          return false;
        }

        // Check non-strict calls
        if (functionName.equals(Operator.LOGICAL_AND.getFunction())
            || functionName.equals(Operator.LOGICAL_OR.getFunction())) {

          // If any element is a constant, this could be a foldable expr (e.g: x && false -> x)
          return celCall.args().stream()
              .anyMatch(node -> node.exprKind().getKind().equals(Kind.CONSTANT));
        }

        if (functionName.equals(Operator.CONDITIONAL.getFunction())) {
          CelExpr cond = celCall.args().get(0);

          // A ternary with a constant condition is trivially foldable
          return cond.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE);
        }

        if (functionName.equals(Operator.IN.getFunction())) {
          return true;
        }

        // Default case: all call arguments must be constants. If the argument is a container (ex:
        // list, map), then its arguments must be a constant.
        return areChildrenArgConstant(navigableExpr);
      case SELECT:
        CelNavigableExpr operand = navigableExpr.children().collect(onlyElement());
        return areChildrenArgConstant(operand);
      case COMPREHENSION:
        return !isNestedComprehension(navigableExpr);
      default:
        return false;
    }
  }

  private static boolean areChildrenArgConstant(CelNavigableExpr expr) {
    if (expr.getKind().equals(Kind.CONSTANT)) {
      return true;
    }

    if (expr.getKind().equals(Kind.CALL)
        || expr.getKind().equals(Kind.CREATE_LIST)
        || expr.getKind().equals(Kind.CREATE_MAP)
        || expr.getKind().equals(Kind.CREATE_STRUCT)) {
      return expr.children().allMatch(ConstantFoldingOptimizer::areChildrenArgConstant);
    }

    return false;
  }

  private static boolean isNestedComprehension(CelNavigableExpr expr) {
    Optional<CelNavigableExpr> maybeParent = expr.parent();
    while (maybeParent.isPresent()) {
      CelNavigableExpr parent = maybeParent.get();
      if (parent.getKind().equals(Kind.COMPREHENSION)) {
        return true;
      }
      maybeParent = parent.parent();
    }

    return false;
  }

  private Optional<CelAbstractSyntaxTree> maybeFold(
      Cel cel, CelAbstractSyntaxTree ast, CelExpr expr) throws CelOptimizationException {
    Object result;
    try {
      result = CelExprUtil.evaluateExpr(cel, expr);
    } catch (CelValidationException | CelEvaluationException e) {
      throw new CelOptimizationException(
          "Constant folding failure. Failed to evaluate subtree due to: " + e.getMessage(), e);
    }

    // Rewrite optional calls to use the sentinel optional functions.
    // ex1: optional.ofNonZeroValue(0) -> optional.none().
    // ex2: optional.ofNonZeroValue(5) -> optional.of(5)
    if (result instanceof Optional<?>) {
      Optional<?> optResult = ((Optional<?>) result);
      return maybeRewriteOptional(optResult, ast, expr);
    }

    return maybeAdaptEvaluatedResult(result)
        .map(celExpr -> replaceSubtree(ast, celExpr, expr.id()));
  }

  private Optional<CelExpr> maybeAdaptEvaluatedResult(Object result) {
    if (CelConstant.isConstantValue(result)) {
      return Optional.of(
          CelExpr.newBuilder().setConstant(CelConstant.ofObjectValue(result)).build());
    } else if (result instanceof Collection<?>) {
      Collection<?> collection = (Collection<?>) result;
      CelCreateList.Builder createListBuilder = CelCreateList.newBuilder();
      for (Object evaluatedElement : collection) {
        Optional<CelExpr> adaptedExpr = maybeAdaptEvaluatedResult(evaluatedElement);
        if (!adaptedExpr.isPresent()) {
          return Optional.empty();
        }
        createListBuilder.addElements(adaptedExpr.get());
      }

      return Optional.of(CelExpr.newBuilder().setCreateList(createListBuilder.build()).build());
    } else if (result instanceof Map<?, ?>) {
      Map<?, ?> map = (Map<?, ?>) result;
      CelCreateMap.Builder createMapBuilder = CelCreateMap.newBuilder();
      for (Entry<?, ?> entry : map.entrySet()) {
        Optional<CelExpr> adaptedKey = maybeAdaptEvaluatedResult(entry.getKey());
        if (!adaptedKey.isPresent()) {
          return Optional.empty();
        }
        Optional<CelExpr> adaptedValue = maybeAdaptEvaluatedResult(entry.getValue());
        if (!adaptedValue.isPresent()) {
          return Optional.empty();
        }

        createMapBuilder.addEntries(
            CelCreateMap.Entry.newBuilder()
                .setKey(adaptedKey.get())
                .setValue(adaptedValue.get())
                .build());
      }

      return Optional.of(CelExpr.newBuilder().setCreateMap(createMapBuilder.build()).build());
    }

    // Evaluated result cannot be folded (e.g: unknowns)
    return Optional.empty();
  }

  private Optional<CelAbstractSyntaxTree> maybeRewriteOptional(
      Optional<?> optResult, CelAbstractSyntaxTree ast, CelExpr expr) {
    if (!optResult.isPresent()) {
      if (!expr.callOrDefault().function().equals(OPTIONAL_NONE_FUNCTION)) {
        // An empty optional value was encountered. Rewrite the tree with optional.none call.
        // This is to account for other optional functions returning an empty optional value
        // e.g: optional.ofNonZeroValue(0)
        return Optional.of(replaceSubtree(ast, OPTIONAL_NONE_EXPR, expr.id()));
      }
    } else if (!expr.callOrDefault().function().equals(OPTIONAL_OF_FUNCTION)) {
      Object unwrappedResult = optResult.get();
      if (!CelConstant.isConstantValue(unwrappedResult)) {
        // Evaluated result is not a constant. Leave the optional as is.
        return Optional.empty();
      }

      CelExpr newOptionalOfCall =
          CelExpr.newBuilder()
              .setCall(
                  CelCall.newBuilder()
                      .setFunction(OPTIONAL_OF_FUNCTION)
                      .addArgs(
                          CelExpr.newBuilder()
                              .setConstant(CelConstant.ofObjectValue(unwrappedResult))
                              .build())
                      .build())
              .build();
      return Optional.of(replaceSubtree(ast, newOptionalOfCall, expr.id()));
    }

    return Optional.empty();
  }

  /** Inspects the non-strict calls to determine whether a branch can be removed. */
  private Optional<CelAbstractSyntaxTree> maybePruneBranches(
      CelAbstractSyntaxTree ast, CelExpr expr) {
    if (!expr.exprKind().getKind().equals(Kind.CALL)) {
      return Optional.empty();
    }

    CelCall call = expr.call();
    String function = call.function();
    if (function.equals(Operator.LOGICAL_AND.getFunction())
        || function.equals(Operator.LOGICAL_OR.getFunction())) {
      return maybeShortCircuitCall(ast, expr);
    } else if (function.equals(Operator.CONDITIONAL.getFunction())) {
      CelExpr cond = call.args().get(0);
      CelExpr truthy = call.args().get(1);
      CelExpr falsy = call.args().get(2);
      if (!cond.exprKind().getKind().equals(Kind.CONSTANT)) {
        throw new IllegalStateException(
            String.format(
                "Expected constant condition. Got: %s instead.", cond.exprKind().getKind()));
      }
      CelExpr result = cond.constant().booleanValue() ? truthy : falsy;

      return Optional.of(replaceSubtree(ast, result, expr.id()));
    } else if (function.equals(Operator.IN.getFunction())) {
      CelCreateList haystack = call.args().get(1).createList();
      if (haystack.elements().isEmpty()) {
        return Optional.of(
            replaceSubtree(
                ast,
                CelExpr.newBuilder().setConstant(CelConstant.ofValue(false)).build(),
                expr.id()));
      }

      CelExpr needle = call.args().get(0);
      if (needle.exprKind().getKind().equals(Kind.CONSTANT)
          || needle.exprKind().getKind().equals(Kind.IDENT)) {
        Object needleValue =
            needle.exprKind().getKind().equals(Kind.CONSTANT) ? needle.constant() : needle.ident();
        for (CelExpr elem : haystack.elements()) {
          if (elem.constantOrDefault().equals(needleValue)
              || elem.identOrDefault().equals(needleValue)) {
            return Optional.of(
                replaceSubtree(
                    ast,
                    CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(),
                    expr.id()));
          }
        }
      }
    }

    return Optional.empty();
  }

  private Optional<CelAbstractSyntaxTree> maybeShortCircuitCall(
      CelAbstractSyntaxTree ast, CelExpr expr) {
    CelCall call = expr.call();
    boolean shortCircuit = false;
    boolean skip = true;
    if (call.function().equals(Operator.LOGICAL_OR.getFunction())) {
      shortCircuit = true;
      skip = false;
    }
    ImmutableList.Builder<CelExpr> newArgsBuilder = new ImmutableList.Builder<>();

    for (CelExpr arg : call.args()) {
      if (!arg.exprKind().getKind().equals(Kind.CONSTANT)) {
        newArgsBuilder.add(arg);
        continue;
      }
      if (arg.constant().booleanValue() == skip) {
        continue;
      }

      if (arg.constant().booleanValue() == shortCircuit) {
        return Optional.of(replaceSubtree(ast, arg, expr.id()));
      }
    }

    ImmutableList<CelExpr> newArgs = newArgsBuilder.build();
    if (newArgs.isEmpty()) {
      return Optional.of(replaceSubtree(ast, call.args().get(0), expr.id()));
    }
    if (newArgs.size() == 1) {
      return Optional.of(replaceSubtree(ast, newArgs.get(0), expr.id()));
    }

    // TODO: Support folding variadic AND/ORs.
    throw new UnsupportedOperationException(
        "Folding variadic logical operator is not supported yet.");
  }

  private CelAbstractSyntaxTree pruneOptionalElements(CelNavigableAst navigableAst) {
    ImmutableList<CelExpr> aggregateLiterals =
        navigableAst
            .getRoot()
            .allNodes()
            .filter(
                node ->
                    node.getKind().equals(Kind.CREATE_LIST)
                        || node.getKind().equals(Kind.CREATE_MAP)
                        || node.getKind().equals(Kind.CREATE_STRUCT))
            .map(CelNavigableExpr::expr)
            .collect(toImmutableList());

    CelAbstractSyntaxTree ast = navigableAst.getAst();
    for (CelExpr expr : aggregateLiterals) {
      switch (expr.exprKind().getKind()) {
        case CREATE_LIST:
          ast = pruneOptionalListElements(ast, expr);
          break;
        case CREATE_MAP:
          ast = pruneOptionalMapElements(ast, expr);
          break;
        case CREATE_STRUCT:
          ast = pruneOptionalStructElements(ast, expr);
          break;
        default:
          throw new IllegalArgumentException("Unexpected exprKind: " + expr.exprKind());
      }
    }
    return ast;
  }

  private CelAbstractSyntaxTree pruneOptionalListElements(CelAbstractSyntaxTree ast, CelExpr expr) {
    CelCreateList createList = expr.createList();
    if (createList.optionalIndices().isEmpty()) {
      return ast;
    }

    HashSet<Integer> optionalIndices = new HashSet<>(createList.optionalIndices());
    ImmutableList.Builder<CelExpr> updatedElemBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<Integer> updatedIndicesBuilder = new ImmutableList.Builder<>();
    int newOptIndex = -1;
    for (int i = 0; i < createList.elements().size(); i++) {
      newOptIndex++;
      CelExpr element = createList.elements().get(i);
      if (!optionalIndices.contains(i)) {
        updatedElemBuilder.add(element);
        continue;
      }

      if (element.exprKind().getKind().equals(Kind.CALL)) {
        CelCall call = element.call();
        if (call.function().equals(OPTIONAL_NONE_FUNCTION)) {
          // Skip optional.none.
          // Skipping causes the list to get smaller.
          newOptIndex--;
          continue;
        } else if (call.function().equals(OPTIONAL_OF_FUNCTION)) {
          CelExpr arg = call.args().get(0);
          if (arg.exprKind().getKind().equals(Kind.CONSTANT)) {
            updatedElemBuilder.add(call.args().get(0));
            continue;
          }
        }
      }

      updatedElemBuilder.add(element);
      updatedIndicesBuilder.add(newOptIndex);
    }

    return replaceSubtree(
        ast,
        CelExpr.newBuilder()
            .setCreateList(
                CelCreateList.newBuilder()
                    .addElements(updatedElemBuilder.build())
                    .addOptionalIndices(updatedIndicesBuilder.build())
                    .build())
            .build(),
        expr.id());
  }

  private CelAbstractSyntaxTree pruneOptionalMapElements(CelAbstractSyntaxTree ast, CelExpr expr) {
    CelCreateMap createMap = expr.createMap();
    ImmutableList.Builder<CelCreateMap.Entry> updatedEntryBuilder = new ImmutableList.Builder<>();
    boolean modified = false;
    for (CelCreateMap.Entry entry : createMap.entries()) {
      CelExpr key = entry.key();
      Kind keyKind = key.exprKind().getKind();
      CelExpr value = entry.value();
      Kind valueKind = value.exprKind().getKind();
      if (!entry.optionalEntry()
          || !keyKind.equals(Kind.CONSTANT)
          || !valueKind.equals(Kind.CALL)) {
        updatedEntryBuilder.add(entry);
        continue;
      }

      CelCall call = value.call();
      if (call.function().equals(OPTIONAL_NONE_FUNCTION)) {
        // Skip the element. This is resolving an optional.none: ex {?1: optional.none()}.
        modified = true;
        continue;
      } else if (call.function().equals(OPTIONAL_OF_FUNCTION)) {
        CelExpr arg = call.args().get(0);
        if (arg.exprKind().getKind().equals(Kind.CONSTANT)) {
          modified = true;
          updatedEntryBuilder.add(
              entry.toBuilder().setOptionalEntry(false).setValue(call.args().get(0)).build());
          continue;
        }
      }

      updatedEntryBuilder.add(entry);
    }

    if (modified) {
      return replaceSubtree(
          ast,
          CelExpr.newBuilder()
              .setCreateMap(
                  CelCreateMap.newBuilder().addEntries(updatedEntryBuilder.build()).build())
              .build(),
          expr.id());
    }

    return ast;
  }

  private CelAbstractSyntaxTree pruneOptionalStructElements(
      CelAbstractSyntaxTree ast, CelExpr expr) {
    CelCreateStruct createStruct = expr.createStruct();
    ImmutableList.Builder<CelCreateStruct.Entry> updatedEntryBuilder =
        new ImmutableList.Builder<>();
    boolean modified = false;
    for (CelCreateStruct.Entry entry : createStruct.entries()) {
      CelExpr value = entry.value();
      Kind valueKind = value.exprKind().getKind();
      if (!entry.optionalEntry() || !valueKind.equals(Kind.CALL)) {
        // Preserve the entry as is
        updatedEntryBuilder.add(entry);
        continue;
      }

      CelCall call = value.call();
      if (call.function().equals(OPTIONAL_NONE_FUNCTION)) {
        // Skip the element. This is resolving an optional.none: ex msg{?field: optional.none()}.
        modified = true;
        continue;
      } else if (call.function().equals(OPTIONAL_OF_FUNCTION)) {
        CelExpr arg = call.args().get(0);
        if (arg.exprKind().getKind().equals(Kind.CONSTANT)) {
          modified = true;
          updatedEntryBuilder.add(
              entry.toBuilder().setOptionalEntry(false).setValue(call.args().get(0)).build());
          continue;
        }
      }

      updatedEntryBuilder.add(entry);
    }

    if (modified) {
      return replaceSubtree(
          ast,
          CelExpr.newBuilder()
              .setCreateStruct(
                  CelCreateStruct.newBuilder()
                      .setMessageName(createStruct.messageName())
                      .addEntries(updatedEntryBuilder.build())
                      .build())
              .build(),
          expr.id());
    }

    return ast;
  }

  private ConstantFoldingOptimizer() {}
}
