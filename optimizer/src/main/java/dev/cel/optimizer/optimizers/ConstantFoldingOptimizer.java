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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelValidationException;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelExprUtil;
import dev.cel.common.ast.MutableAst;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.ast.MutableExpr;
import dev.cel.common.ast.MutableExpr.MutableCall;
import dev.cel.common.ast.MutableExpr.MutableCreateList;
import dev.cel.common.ast.MutableExpr.MutableCreateMap;
import dev.cel.common.ast.MutableExpr.MutableCreateStruct;
import dev.cel.common.ast.MutableExprConverter;
import dev.cel.extensions.CelOptionalLibrary.Function;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelEvaluationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

/**
 * Performs optimization for inlining constant scalar and aggregate literal values within function
 * calls and select statements with their evaluated result.
 */
public final class ConstantFoldingOptimizer implements CelAstOptimizer {
  private static final ConstantFoldingOptimizer INSTANCE =
      new ConstantFoldingOptimizer(ConstantFoldingOptions.newBuilder().build());

  /** Returns a default instance of constant folding optimizer with preconfigured defaults. */
  public static ConstantFoldingOptimizer getInstance() {
    return INSTANCE;
  }

  /**
   * Returns a new instance of constant folding optimizer configured with the provided {@link
   * ConstantFoldingOptions}.
   */
  public static ConstantFoldingOptimizer newInstance(
      ConstantFoldingOptions constantFoldingOptions) {
    return new ConstantFoldingOptimizer(constantFoldingOptions);
  }

  private final ConstantFoldingOptions constantFoldingOptions;
  private final AstMutator astMutator;

  // Use optional.of and optional.none as sentinel function names for folding optional calls.
  // TODO: Leverage CelValue representation of Optionals instead when available.
  private static MutableExpr newOptionalNoneExpr() {
    return MutableExpr.ofCall(
        MutableCall.create(Function.OPTIONAL_NONE.getFunction())
    );
  }

  @Override
  public OptimizationResult optimize(CelNavigableAst navigableAst, Cel cel)
      throws CelOptimizationException {
    MutableAst mutableAst = MutableAst.fromCelAst(navigableAst.getAst());
    Set<MutableExpr> visitedExprs = new HashSet<>();
    int iterCount = 0;
    while (true) {
      iterCount++;
      if (iterCount >= constantFoldingOptions.maxIterationLimit()) {
        throw new IllegalStateException("Max iteration count reached.");
      }
      Optional<CelNavigableExpr> foldableExpr =
          CelNavigableExpr.fromMutableExpr(mutableAst.mutableExpr())
              .allNodes()
              .filter(ConstantFoldingOptimizer::canFold)
              .filter(node -> !visitedExprs.contains(node.mutableExpr()))
              .findAny();
      if (!foldableExpr.isPresent()) {
        break;
      }
      visitedExprs.add(foldableExpr.get().mutableExpr());

      Optional<MutableAst> mutatedResult;
      // Attempt to prune if it is a non-strict call
      mutatedResult = maybePruneBranches(mutableAst, foldableExpr.get().mutableExpr());
      if (!mutatedResult.isPresent()) {
        // Evaluate the call then fold
        mutatedResult = maybeFold(cel, mutableAst, foldableExpr.get());
      }

      if (!mutatedResult.isPresent()) {
        // Skip this expr. It's neither prune-able nor foldable.
        continue;
      }

      visitedExprs.clear();
      mutableAst = mutatedResult.get();
    }

    // If the output is a list, map, or struct which contains optional entries, then prune it
    // to make sure that the optionals, if resolved, do not surface in the output literal.
    mutableAst = pruneOptionalElements(mutableAst);
    return OptimizationResult.create(astMutator.renumberIdsConsecutively(mutableAst).toParsedAst());
  }

  private static boolean canFold(CelNavigableExpr navigableExpr) {
    switch (navigableExpr.getKind()) {
      case CALL:
        MutableCall mutableCall = navigableExpr.mutableExpr().call();
        String functionName = mutableCall.function();

        // These are already folded or do not need to be folded.
        if (functionName.equals(Function.OPTIONAL_OF.getFunction())
            || functionName.equals(Function.OPTIONAL_NONE.getFunction())) {
          return false;
        }

        // Check non-strict calls
        if (functionName.equals(Operator.LOGICAL_AND.getFunction())
            || functionName.equals(Operator.LOGICAL_OR.getFunction())) {

          // If any element is a constant, this could be a foldable expr (e.g: x && false -> x)
          return mutableCall.args().stream()
              .anyMatch(node -> node.exprKind().equals(Kind.CONSTANT));
        }

        if (functionName.equals(Operator.CONDITIONAL.getFunction())) {
          MutableExpr cond = mutableCall.args().get(0);

          // A ternary with a constant condition is trivially foldable
          return cond.exprKind().equals(Kind.CONSTANT) && cond.constant().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE);
        }

        if (functionName.equals(Operator.IN.getFunction())) {
          return canFoldInOperator(navigableExpr);
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

  private static boolean canFoldInOperator(CelNavigableExpr navigableExpr) {
    ImmutableList<CelNavigableExpr> allIdents =
        navigableExpr
            .allNodes()
            .filter(node -> node.getKind().equals(Kind.IDENT))
            .collect(toImmutableList());
    for (CelNavigableExpr identNode : allIdents) {
      CelNavigableExpr parent = identNode.parent().orElse(null);
      while (parent != null) {
        if (parent.getKind().equals(Kind.COMPREHENSION)) {
          if (parent.mutableExpr().comprehension().accuVar().equals(identNode.mutableExpr().ident().name())) {
            // Prevent folding a subexpression if it contains a variable declared by a
            // comprehension. The subexpression cannot be compiled without the full context of the
            // surrounding comprehension.
            return false;
          }
        }
        parent = parent.parent().orElse(null);
      }
    }

    return true;
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

  private Optional<MutableAst> maybeFold(
      Cel cel, MutableAst mutableAst, CelNavigableExpr node) throws CelOptimizationException {
    Object result;
    try {
      result = CelExprUtil.evaluateExpr(cel, MutableExprConverter.fromMutableExpr(node.mutableExpr()));
    } catch (CelValidationException | CelEvaluationException e) {
      throw new CelOptimizationException(
          "Constant folding failure. Failed to evaluate subtree due to: " + e.getMessage(), e);
    }

    // Rewrite optional calls to use the sentinel optional functions.
    // ex1: optional.ofNonZeroValue(0) -> optional.none().
    // ex2: optional.ofNonZeroValue(5) -> optional.of(5)
    if (result instanceof Optional<?>) {
      Optional<?> optResult = ((Optional<?>) result);
      return maybeRewriteOptional(optResult, mutableAst, node.mutableExpr());
    }

    return maybeAdaptEvaluatedResult(result)
        .map(celExpr -> astMutator.replaceSubtree(mutableAst.mutableExpr(), celExpr, node.id(), mutableAst.source()));
  }

  private Optional<MutableExpr> maybeAdaptEvaluatedResult(Object result) {
    if (CelConstant.isConstantValue(result)) {
      return Optional.of(
          MutableExpr.ofConstant(CelConstant.ofObjectValue(result)));
    } else if (result instanceof Collection<?>) {
      Collection<?> collection = (Collection<?>) result;
      List<MutableExpr> listElements = new ArrayList<>();
      for (Object evaluatedElement : collection) {
        MutableExpr adaptedExpr = maybeAdaptEvaluatedResult(evaluatedElement).orElse(null);
        if (adaptedExpr == null) {
          return Optional.empty();
        }
        listElements.add(adaptedExpr);
      }

      return Optional.of(MutableExpr.ofCreateList(MutableCreateList.create(listElements)));
    } else if (result instanceof Map<?, ?>) {
      Map<?, ?> map = (Map<?, ?>) result;
      List<MutableCreateMap.Entry> mapEntries = new ArrayList<>();
      for (Entry<?, ?> entry : map.entrySet()) {
        MutableExpr adaptedKey = maybeAdaptEvaluatedResult(entry.getKey()).orElse(null);
        if (adaptedKey == null) {
          return Optional.empty();
        }
        MutableExpr adaptedValue = maybeAdaptEvaluatedResult(entry.getValue()).orElse(null);
        if (adaptedValue == null) {
          return Optional.empty();
        }

        mapEntries.add(MutableCreateMap.Entry.create(
            adaptedKey,
            adaptedValue
        ));
      }

      return Optional.of(MutableExpr.ofCreateMap(MutableCreateMap.create(mapEntries)));
    }

    // Evaluated result cannot be folded (e.g: unknowns)
    return Optional.empty();
  }

  private Optional<MutableAst> maybeRewriteOptional(
      Optional<?> optResult, MutableAst mutableAst, MutableExpr expr) {
    if (!optResult.isPresent()) {
      if (!expr.call().function().equals(Function.OPTIONAL_NONE.getFunction())) {
        // An empty optional value was encountered. Rewrite the tree with optional.none call.
        // This is to account for other optional functions returning an empty optional value
        // e.g: optional.ofNonZeroValue(0)
        return Optional.of(astMutator.replaceSubtree(mutableAst.mutableExpr(), newOptionalNoneExpr(), expr.id(), mutableAst.source()));
      }
    } else if (!expr.call().function().equals(Function.OPTIONAL_OF.getFunction())) {
      Object unwrappedResult = optResult.get();
      if (!CelConstant.isConstantValue(unwrappedResult)) {
        // Evaluated result is not a constant. Leave the optional as is.
        return Optional.empty();
      }

      MutableExpr newOptionalOfCall =
          MutableExpr.ofCall(
              MutableCall.create(
              Function.OPTIONAL_OF.getFunction(),
              MutableExpr.ofConstant(CelConstant.ofObjectValue(unwrappedResult))
          ));

      return Optional.of(astMutator.replaceSubtree(mutableAst.mutableExpr(), newOptionalOfCall, expr.id(), mutableAst.source()));
    }

    return Optional.empty();
  }

  /** Inspects the non-strict calls to determine whether a branch can be removed. */
  private Optional<MutableAst> maybePruneBranches(
      MutableAst mutableAst, MutableExpr expr) {
    if (!expr.exprKind().equals(Kind.CALL)) {
      return Optional.empty();
    }

    MutableCall call = expr.call();
    String function = call.function();
    if (function.equals(Operator.LOGICAL_AND.getFunction())
        || function.equals(Operator.LOGICAL_OR.getFunction())) {
      return maybeShortCircuitCall(mutableAst, expr);
    } else if (function.equals(Operator.CONDITIONAL.getFunction())) {
      MutableExpr cond = call.args().get(0);
      MutableExpr truthy = call.args().get(1);
      MutableExpr falsy = call.args().get(2);
      if (!cond.exprKind().equals(Kind.CONSTANT)) {
        throw new IllegalStateException(
            String.format(
                "Expected constant condition. Got: %s instead.", cond.exprKind()));
      }
      MutableExpr result = cond.constant().booleanValue() ? truthy : falsy;

      return Optional.of(astMutator.replaceSubtree(mutableAst.mutableExpr(), result, expr.id(), mutableAst.source()));
    } else if (function.equals(Operator.IN.getFunction())) {
      MutableExpr callArg = call.args().get(1);
      if (!callArg.exprKind().equals(Kind.CREATE_LIST)) {
        return Optional.empty();
      }

      MutableCreateList haystack = callArg.createList();
      if (haystack.elements().isEmpty()) {
        return Optional.of(
            astMutator.replaceSubtree(
                mutableAst.mutableExpr(),
                MutableExpr.ofConstant(CelConstant.ofValue(false)),
                expr.id(),
                mutableAst.source()
            ));
      }

      MutableExpr needle = call.args().get(0);
      if (needle.exprKind().equals(Kind.CONSTANT)
          || needle.exprKind().equals(Kind.IDENT)) {
        Object needleValue =
            needle.exprKind().equals(Kind.CONSTANT) ? needle.constant() : needle.ident();
        for (MutableExpr elem : haystack.elements()) {
          if ((elem.exprKind().equals(Kind.CONSTANT) && elem.constant().equals(needleValue))
              || (elem.exprKind().equals(Kind.IDENT) && elem.ident().equals(needleValue))) {
            return Optional.of(
                astMutator.replaceSubtree(
                    mutableAst.mutableExpr(),
                    MutableExpr.ofConstant(CelConstant.ofValue(true)),
                    expr.id(),
                    mutableAst.source()
                )
            );
          }
        }
      }
    }

    return Optional.empty();
  }

  private Optional<MutableAst> maybeShortCircuitCall(
      MutableAst mutableAst, MutableExpr expr) {
    MutableCall call = expr.call();
    boolean shortCircuit = false;
    boolean skip = true;
    if (call.function().equals(Operator.LOGICAL_OR.getFunction())) {
      shortCircuit = true;
      skip = false;
    }
    ImmutableList.Builder<MutableExpr> newArgsBuilder = new ImmutableList.Builder<>();

    for (MutableExpr arg : call.args()) {
      if (!arg.exprKind().equals(Kind.CONSTANT)) {
        newArgsBuilder.add(arg);
        continue;
      }
      if (arg.constant().booleanValue() == skip) {
        continue;
      }

      if (arg.constant().booleanValue() == shortCircuit) {
        return Optional.of(astMutator.replaceSubtree(mutableAst.mutableExpr(), arg, expr.id(),
            mutableAst.source()));
      }
    }

    ImmutableList<MutableExpr> newArgs = newArgsBuilder.build();
    if (newArgs.isEmpty()) {
      return Optional.of(astMutator.replaceSubtree(mutableAst.mutableExpr(), call.args().get(0), expr.id(), mutableAst.source()));
    }
    if (newArgs.size() == 1) {
      return Optional.of(astMutator.replaceSubtree(mutableAst.mutableExpr(), newArgs.get(0), expr.id(), mutableAst.source()));
    }

    // TODO: Support folding variadic AND/ORs.
    throw new UnsupportedOperationException(
        "Folding variadic logical operator is not supported yet.");
  }

  private MutableAst pruneOptionalElements(MutableAst ast) {
    ImmutableList<MutableExpr> aggregateLiterals =
        CelNavigableExpr.fromMutableExpr(ast.mutableExpr())
            .allNodes()
            .filter(
                node ->
                    node.getKind().equals(Kind.CREATE_LIST)
                        || node.getKind().equals(Kind.CREATE_MAP)
                        || node.getKind().equals(Kind.CREATE_STRUCT))
            .map(CelNavigableExpr::mutableExpr)
            .collect(toImmutableList());

    for (MutableExpr expr : aggregateLiterals) {
      switch (expr.exprKind()) {
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

  private MutableAst pruneOptionalListElements(MutableAst mutableAst, MutableExpr expr) {
    MutableCreateList createList = expr.createList();
    if (createList.optionalIndices().isEmpty()) {
      return mutableAst;
    }

    HashSet<Integer> optionalIndices = new HashSet<>(createList.optionalIndices());
    ImmutableList.Builder<MutableExpr> updatedElemBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<Integer> updatedIndicesBuilder = new ImmutableList.Builder<>();
    int newOptIndex = -1;
    for (int i = 0; i < createList.elements().size(); i++) {
      newOptIndex++;
      MutableExpr element = createList.elements().get(i);
      if (!optionalIndices.contains(i)) {
        updatedElemBuilder.add(element);
        continue;
      }

      if (element.exprKind().equals(Kind.CALL)) {
        MutableCall call = element.call();
        if (call.function().equals(Function.OPTIONAL_NONE.getFunction())) {
          // Skip optional.none.
          // Skipping causes the list to get smaller.
          newOptIndex--;
          continue;
        } else if (call.function().equals(Function.OPTIONAL_OF.getFunction())) {
          MutableExpr arg = call.args().get(0);
          if (arg.exprKind().equals(Kind.CONSTANT)) {
            updatedElemBuilder.add(call.args().get(0));
            continue;
          }
        }
      }

      updatedElemBuilder.add(element);
      updatedIndicesBuilder.add(newOptIndex);
    }

    return astMutator.replaceSubtree(
        mutableAst.mutableExpr(),
        MutableExpr.ofCreateList(
            MutableCreateList.create(
                updatedElemBuilder.build(),
                updatedIndicesBuilder.build()
            )
        ),
        expr.id(),
        mutableAst.source()
    );
  }

  private MutableAst pruneOptionalMapElements(MutableAst ast, MutableExpr expr) {
    MutableCreateMap createMap = expr.createMap();
    ImmutableList.Builder<MutableCreateMap.Entry> updatedEntryBuilder = new ImmutableList.Builder<>();
    boolean modified = false;
    for (MutableCreateMap.Entry entry : createMap.entries()) {
      MutableExpr key = entry.key();
      Kind keyKind = key.exprKind();
      MutableExpr value = entry.value();
      Kind valueKind = value.exprKind();
      if (!entry.optionalEntry()
          || !keyKind.equals(Kind.CONSTANT)
          || !valueKind.equals(Kind.CALL)) {
        updatedEntryBuilder.add(entry);
        continue;
      }

      MutableCall call = value.call();
      if (call.function().equals(Function.OPTIONAL_NONE.getFunction())) {
        // Skip the element. This is resolving an optional.none: ex {?1: optional.none()}.
        modified = true;
        continue;
      } else if (call.function().equals(Function.OPTIONAL_OF.getFunction())) {
        MutableExpr arg = call.args().get(0);
        if (arg.exprKind().equals(Kind.CONSTANT)) {
          modified = true;
          entry.setOptionalEntry(false);
          entry.setValue(call.args().get(0));
          updatedEntryBuilder.add(entry);
          continue;
        }
      }

      updatedEntryBuilder.add(entry);
    }

    if (modified) {
      return astMutator.replaceSubtree(
          ast.mutableExpr(),
          MutableExpr.ofCreateMap(
              MutableCreateMap.create(
                  updatedEntryBuilder.build()
              )
          ),
          expr.id(),
          ast.source()
          );
    }

    return ast;
  }

  private MutableAst pruneOptionalStructElements(MutableAst ast, MutableExpr expr) {
    MutableCreateStruct createStruct = expr.createStruct();
    ImmutableList.Builder<MutableCreateStruct.Entry> updatedEntryBuilder =
        new ImmutableList.Builder<>();
    boolean modified = false;
    for (MutableCreateStruct.Entry entry : createStruct.entries()) {
      MutableExpr value = entry.value();
      Kind valueKind = value.exprKind();
      if (!entry.optionalEntry() || !valueKind.equals(Kind.CALL)) {
        // Preserve the entry as is
        updatedEntryBuilder.add(entry);
        continue;
      }

      MutableCall call = value.call();
      if (call.function().equals(Function.OPTIONAL_NONE.getFunction())) {
        // Skip the element. This is resolving an optional.none: ex msg{?field: optional.none()}.
        modified = true;
        continue;
      } else if (call.function().equals(Function.OPTIONAL_OF.getFunction())) {
        MutableExpr arg = call.args().get(0);
        if (arg.exprKind().equals(Kind.CONSTANT)) {
          modified = true;
          entry.setOptionalEntry(false);
          entry.setValue(call.args().get(0));
          updatedEntryBuilder.add(entry);
          continue;
        }
      }

      updatedEntryBuilder.add(entry);
    }

    if (modified) {
      return astMutator.replaceSubtree(
          ast.mutableExpr(),
          MutableExpr.ofCreateStruct(
              MutableCreateStruct.create(createStruct.messageName(), updatedEntryBuilder.build())
          ),
          expr.id(),
          ast.source()
      );
    }

    return ast;
  }

  /** Options to configure how Constant Folding behave. */
  @AutoValue
  public abstract static class ConstantFoldingOptions {
    public abstract int maxIterationLimit();

    /** Builder for configuring the {@link ConstantFoldingOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /**
       * Limit the number of iteration while performing constant folding. An exception is thrown if
       * the iteration count exceeds the set value.
       */
      public abstract Builder maxIterationLimit(int value);

      public abstract ConstantFoldingOptions build();

      Builder() {}
    }

    /** Returns a new options builder with recommended defaults pre-configured. */
    public static Builder newBuilder() {
      return new AutoValue_ConstantFoldingOptimizer_ConstantFoldingOptions.Builder()
          .maxIterationLimit(400);
    }

    ConstantFoldingOptions() {}
  }

  private ConstantFoldingOptimizer(ConstantFoldingOptions constantFoldingOptions) {
    this.constantFoldingOptions = constantFoldingOptions;
    this.astMutator = AstMutator.newInstance(constantFoldingOptions.maxIterationLimit());
  }
}
