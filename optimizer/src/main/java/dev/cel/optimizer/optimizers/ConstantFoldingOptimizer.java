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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExpr.CelMutableList;
import dev.cel.common.ast.CelMutableExpr.CelMutableMap;
import dev.cel.common.ast.CelMutableExpr.CelMutableStruct;
import dev.cel.common.ast.CelMutableExprConverter;
import dev.cel.common.navigation.CelNavigableMutableAst;
import dev.cel.common.navigation.CelNavigableMutableExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelOptionalLibrary.Function;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelEvaluationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

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
  private final ImmutableSet<String> foldableFunctions;

  // Use optional.of and optional.none as sentinel function names for folding optional calls.
  // TODO: Leverage CelValue representation of Optionals instead when available.
  private static CelMutableExpr newOptionalNoneExpr() {
    return CelMutableExpr.ofCall(CelMutableCall.create(Function.OPTIONAL_NONE.getFunction()));
  }

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel)
      throws CelOptimizationException {
    // Override the environment's expected type to generally allow all subtrees to be folded.
    Cel optimizerEnv = cel.toCelBuilder().setResultType(SimpleType.DYN).build();

    CelMutableAst mutableAst = CelMutableAst.fromCelAst(ast);
    int iterCount = 0;
    boolean continueFolding = true;
    while (continueFolding) {
      if (iterCount >= constantFoldingOptions.maxIterationLimit()) {
        throw new IllegalStateException("Max iteration count reached.");
      }
      iterCount++;
      continueFolding = false;

      ImmutableList<CelNavigableMutableExpr> foldableExprs =
          CelNavigableMutableAst.fromAst(mutableAst)
              .getRoot()
              .allNodes()
              .filter(this::canFold)
              .collect(toImmutableList());
      for (CelNavigableMutableExpr foldableExpr : foldableExprs) {
        iterCount++;

        Optional<CelMutableAst> mutatedResult;
        // Attempt to prune if it is a non-strict call
        mutatedResult = maybePruneBranches(mutableAst, foldableExpr.expr());
        if (!mutatedResult.isPresent()) {
          // Evaluate the call then fold
          mutatedResult = maybeFold(optimizerEnv, mutableAst, foldableExpr);
        }

        if (!mutatedResult.isPresent()) {
          // Skip this expr. It's neither prune-able nor foldable.
          continue;
        }

        continueFolding = true;
        mutableAst = mutatedResult.get();
      }
    }

    // If the output is a list, map, or struct which contains optional entries, then prune it
    // to make sure that the optionals, if resolved, do not surface in the output literal.
    mutableAst = pruneOptionalElements(mutableAst);
    return OptimizationResult.create(astMutator.renumberIdsConsecutively(mutableAst).toParsedAst());
  }

  private boolean canFold(CelNavigableMutableExpr navigableExpr) {
    switch (navigableExpr.getKind()) {
      case CALL:
        if (!containsFoldableFunctionOnly(navigableExpr)) {
          return false;
        }

        CelMutableCall mutableCall = navigableExpr.expr().call();
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
          return mutableCall.args().stream().anyMatch(node -> node.getKind().equals(Kind.CONSTANT));
        }

        if (functionName.equals(Operator.CONDITIONAL.getFunction())) {
          CelMutableExpr cond = mutableCall.args().get(0);

          // A ternary with a constant condition is trivially foldable
          return cond.getKind().equals(Kind.CONSTANT)
              && cond.constant().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE);
        }

        if (functionName.equals(Operator.EQUALS.getFunction())
            || functionName.equals(Operator.NOT_EQUALS.getFunction())) {
          if (mutableCall.args().stream()
                  .anyMatch(node -> isExprConstantOfKind(node, CelConstant.Kind.BOOLEAN_VALUE))
              || mutableCall.args().stream()
                  .allMatch(node -> node.getKind().equals(Kind.CONSTANT))) {
            return true;
          }
        }

        if (functionName.equals(Operator.IN.getFunction())) {
          return canFoldInOperator(navigableExpr);
        }

        // Default case: all call arguments must be constants. If the argument is a container (ex:
        // list, map), then its arguments must be a constant.
        return areChildrenArgConstant(navigableExpr);
      case SELECT:
        CelNavigableMutableExpr operand = navigableExpr.children().collect(onlyElement());
        return areChildrenArgConstant(operand);
      case COMPREHENSION:
        return !isNestedComprehension(navigableExpr);
      default:
        return false;
    }
  }

  private boolean containsFoldableFunctionOnly(CelNavigableMutableExpr navigableExpr) {
    return navigableExpr
        .allNodes()
        .allMatch(
            node -> {
              if (node.getKind().equals(Kind.CALL)) {
                return foldableFunctions.contains(node.expr().call().function());
              }

              return true;
            });
  }

  private static boolean canFoldInOperator(CelNavigableMutableExpr navigableExpr) {
    ImmutableList<CelNavigableMutableExpr> allIdents =
        navigableExpr
            .allNodes()
            .filter(node -> node.getKind().equals(Kind.IDENT))
            .collect(toImmutableList());
    for (CelNavigableMutableExpr identNode : allIdents) {
      CelNavigableMutableExpr parent = identNode.parent().orElse(null);
      while (parent != null) {
        if (parent.getKind().equals(Kind.COMPREHENSION)) {
          String identName = identNode.expr().ident().name();
          CelMutableComprehension parentComprehension = parent.expr().comprehension();
          if (parentComprehension.accuVar().equals(identName)
              || parentComprehension.iterVar().equals(identName)
              || parentComprehension.iterVar2().equals(identName)) {
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

  private static boolean areChildrenArgConstant(CelNavigableMutableExpr expr) {
    if (expr.getKind().equals(Kind.CONSTANT)) {
      return true;
    }

    if (expr.getKind().equals(Kind.CALL)
        || expr.getKind().equals(Kind.LIST)
        || expr.getKind().equals(Kind.MAP)
        || expr.getKind().equals(Kind.STRUCT)) {
      return expr.children().allMatch(ConstantFoldingOptimizer::areChildrenArgConstant);
    }

    return false;
  }

  private static boolean isNestedComprehension(CelNavigableMutableExpr expr) {
    Optional<CelNavigableMutableExpr> maybeParent = expr.parent();
    while (maybeParent.isPresent()) {
      CelNavigableMutableExpr parent = maybeParent.get();
      if (parent.getKind().equals(Kind.COMPREHENSION)) {
        return true;
      }
      maybeParent = parent.parent();
    }

    return false;
  }

  private Optional<CelMutableAst> maybeFold(
      Cel cel, CelMutableAst mutableAst, CelNavigableMutableExpr node)
      throws CelOptimizationException {
    Object result;
    try {
      result = evaluateExpr(cel, CelMutableExprConverter.fromMutableExpr(node.expr()));
    } catch (CelValidationException | CelEvaluationException e) {
      throw new CelOptimizationException(
          "Constant folding failure. Failed to evaluate subtree due to: " + e.getMessage(), e);
    }

    // Rewrite optional calls to use the sentinel optional functions.
    // ex1: optional.ofNonZeroValue(0) -> optional.none().
    // ex2: optional.ofNonZeroValue(5) -> optional.of(5)
    if (result instanceof Optional<?>) {
      Optional<?> optResult = ((Optional<?>) result);
      return maybeRewriteOptional(optResult, mutableAst, node.expr());
    }

    return maybeAdaptEvaluatedResult(result)
        .map(celExpr -> astMutator.replaceSubtree(mutableAst, celExpr, node.id()));
  }

  private Optional<CelMutableExpr> maybeAdaptEvaluatedResult(Object result) {
    if (CelConstant.isConstantValue(result)) {
      return Optional.of(CelMutableExpr.ofConstant(CelConstant.ofObjectValue(result)));
    } else if (result instanceof Collection<?>) {
      Collection<?> collection = (Collection<?>) result;
      List<CelMutableExpr> listElements = new ArrayList<>();
      for (Object evaluatedElement : collection) {
        CelMutableExpr adaptedExpr = maybeAdaptEvaluatedResult(evaluatedElement).orElse(null);
        if (adaptedExpr == null) {
          return Optional.empty();
        }
        listElements.add(adaptedExpr);
      }

      return Optional.of(CelMutableExpr.ofList(CelMutableList.create(listElements)));
    } else if (result instanceof Map<?, ?>) {
      Map<?, ?> map = (Map<?, ?>) result;
      List<CelMutableMap.Entry> mapEntries = new ArrayList<>();
      for (Entry<?, ?> entry : map.entrySet()) {
        CelMutableExpr adaptedKey = maybeAdaptEvaluatedResult(entry.getKey()).orElse(null);
        if (adaptedKey == null) {
          return Optional.empty();
        }
        CelMutableExpr adaptedValue = maybeAdaptEvaluatedResult(entry.getValue()).orElse(null);
        if (adaptedValue == null) {
          return Optional.empty();
        }

        mapEntries.add(CelMutableMap.Entry.create(adaptedKey, adaptedValue));
      }

      return Optional.of(CelMutableExpr.ofMap(CelMutableMap.create(mapEntries)));
    }

    // Evaluated result cannot be folded (e.g: unknowns)
    return Optional.empty();
  }

  private Optional<CelMutableAst> maybeRewriteOptional(
      Optional<?> optResult, CelMutableAst mutableAst, CelMutableExpr expr) {
    if (!optResult.isPresent()) {
      if (!expr.call().function().equals(Function.OPTIONAL_NONE.getFunction())) {
        // An empty optional value was encountered. Rewrite the tree with optional.none call.
        // This is to account for other optional functions returning an empty optional value
        // e.g: optional.ofNonZeroValue(0)
        return Optional.of(astMutator.replaceSubtree(mutableAst, newOptionalNoneExpr(), expr.id()));
      }
    } else if (!expr.call().function().equals(Function.OPTIONAL_OF.getFunction())) {
      Object unwrappedResult = optResult.get();
      if (!CelConstant.isConstantValue(unwrappedResult)) {
        // Evaluated result is not a constant. Leave the optional as is.
        return Optional.empty();
      }

      CelMutableExpr newOptionalOfCall =
          CelMutableExpr.ofCall(
              CelMutableCall.create(
                  Function.OPTIONAL_OF.getFunction(),
                  CelMutableExpr.ofConstant(CelConstant.ofObjectValue(unwrappedResult))));

      return Optional.of(astMutator.replaceSubtree(mutableAst, newOptionalOfCall, expr.id()));
    }

    return Optional.empty();
  }

  /** Inspects the non-strict calls to determine whether a branch can be removed. */
  private Optional<CelMutableAst> maybePruneBranches(
      CelMutableAst mutableAst, CelMutableExpr expr) {
    if (!expr.getKind().equals(Kind.CALL)) {
      return Optional.empty();
    }

    CelMutableCall call = expr.call();
    String function = call.function();
    if (function.equals(Operator.LOGICAL_AND.getFunction())
        || function.equals(Operator.LOGICAL_OR.getFunction())) {
      return maybeShortCircuitCall(mutableAst, expr);
    } else if (function.equals(Operator.CONDITIONAL.getFunction())) {
      CelMutableExpr cond = call.args().get(0);
      CelMutableExpr truthy = call.args().get(1);
      CelMutableExpr falsy = call.args().get(2);
      if (!cond.getKind().equals(Kind.CONSTANT)) {
        throw new IllegalStateException(
            String.format("Expected constant condition. Got: %s instead.", cond.getKind()));
      }
      CelMutableExpr result = cond.constant().booleanValue() ? truthy : falsy;

      return Optional.of(astMutator.replaceSubtree(mutableAst, result, expr.id()));
    } else if (function.equals(Operator.IN.getFunction())) {
      CelMutableExpr callArg = call.args().get(1);
      if (!callArg.getKind().equals(Kind.LIST)) {
        return Optional.empty();
      }

      CelMutableList haystack = callArg.list();
      if (haystack.elements().isEmpty()) {
        return Optional.of(
            astMutator.replaceSubtree(
                mutableAst, CelMutableExpr.ofConstant(CelConstant.ofValue(false)), expr.id()));
      }

      CelMutableExpr needle = call.args().get(0);
      if (needle.getKind().equals(Kind.CONSTANT) || needle.getKind().equals(Kind.IDENT)) {
        Object needleValue =
            needle.getKind().equals(Kind.CONSTANT) ? needle.constant() : needle.ident();
        for (CelMutableExpr elem : haystack.elements()) {
          if ((elem.getKind().equals(Kind.CONSTANT) && elem.constant().equals(needleValue))
              || (elem.getKind().equals(Kind.IDENT) && elem.ident().equals(needleValue))) {
            return Optional.of(
                astMutator.replaceSubtree(
                    mutableAst.expr(),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    expr.id()));
          }
        }
      }
    } else if (function.equals(Operator.EQUALS.getFunction())
        || function.equals(Operator.NOT_EQUALS.getFunction())) {
      CelMutableExpr lhs = call.args().get(0);
      CelMutableExpr rhs = call.args().get(1);
      boolean lhsIsBoolean = isExprConstantOfKind(lhs, CelConstant.Kind.BOOLEAN_VALUE);
      boolean rhsIsBoolean = isExprConstantOfKind(rhs, CelConstant.Kind.BOOLEAN_VALUE);
      boolean invertCondition = function.equals(Operator.NOT_EQUALS.getFunction());
      Optional<CelMutableExpr> replacementExpr = Optional.empty();

      if (lhs.getKind().equals(Kind.CONSTANT) && rhs.getKind().equals(Kind.CONSTANT)) {
        // If both args are const, don't prune any branches and let maybeFold method evaluate this
        // subExpr
        return Optional.empty();
      } else if (lhsIsBoolean) {
        boolean cond = invertCondition != lhs.constant().booleanValue();
        replacementExpr =
            Optional.of(
                cond
                    ? rhs
                    : CelMutableExpr.ofCall(
                        CelMutableCall.create(Operator.LOGICAL_NOT.getFunction(), rhs)));
      } else if (rhsIsBoolean) {
        boolean cond = invertCondition != rhs.constant().booleanValue();
        replacementExpr =
            Optional.of(
                cond
                    ? lhs
                    : CelMutableExpr.ofCall(
                        CelMutableCall.create(Operator.LOGICAL_NOT.getFunction(), lhs)));
      }

      return replacementExpr.map(node -> astMutator.replaceSubtree(mutableAst, node, expr.id()));
    }

    return Optional.empty();
  }

  private Optional<CelMutableAst> maybeShortCircuitCall(
      CelMutableAst mutableAst, CelMutableExpr expr) {
    CelMutableCall call = expr.call();
    boolean shortCircuit = false;
    boolean skip = true;
    if (call.function().equals(Operator.LOGICAL_OR.getFunction())) {
      shortCircuit = true;
      skip = false;
    }
    ImmutableList.Builder<CelMutableExpr> newArgsBuilder = new ImmutableList.Builder<>();

    for (CelMutableExpr arg : call.args()) {
      if (!arg.getKind().equals(Kind.CONSTANT)) {
        newArgsBuilder.add(arg);
        continue;
      }
      if (arg.constant().booleanValue() == skip) {
        continue;
      }

      if (arg.constant().booleanValue() == shortCircuit) {
        return Optional.of(astMutator.replaceSubtree(mutableAst, arg, expr.id()));
      }
    }

    ImmutableList<CelMutableExpr> newArgs = newArgsBuilder.build();
    if (newArgs.isEmpty()) {
      CelMutableExpr shortCircuitTarget =
          call.args().get(0); // either args(0) or args(1) would work here
      return Optional.of(astMutator.replaceSubtree(mutableAst, shortCircuitTarget, expr.id()));
    }
    if (newArgs.size() == 1) {
      return Optional.of(astMutator.replaceSubtree(mutableAst, newArgs.get(0), expr.id()));
    }

    // TODO: Support folding variadic AND/ORs.
    throw new UnsupportedOperationException(
        "Folding variadic logical operator is not supported yet.");
  }

  private CelMutableAst pruneOptionalElements(CelMutableAst ast) {
    ImmutableList<CelMutableExpr> aggregateLiterals =
        CelNavigableMutableExpr.fromExpr(ast.expr())
            .allNodes()
            .filter(
                node ->
                    node.getKind().equals(Kind.LIST)
                        || node.getKind().equals(Kind.MAP)
                        || node.getKind().equals(Kind.STRUCT))
            .map(CelNavigableMutableExpr::expr)
            .collect(toImmutableList());

    for (CelMutableExpr expr : aggregateLiterals) {
      switch (expr.getKind()) {
        case LIST:
          ast = pruneOptionalListElements(ast, expr);
          break;
        case MAP:
          ast = pruneOptionalMapElements(ast, expr);
          break;
        case STRUCT:
          ast = pruneOptionalStructElements(ast, expr);
          break;
        default:
          throw new IllegalArgumentException("Unexpected exprKind: " + expr.getKind());
      }
    }

    return ast;
  }

  private CelMutableAst pruneOptionalListElements(CelMutableAst mutableAst, CelMutableExpr expr) {
    CelMutableList list = expr.list();
    if (list.optionalIndices().isEmpty()) {
      return mutableAst;
    }

    HashSet<Integer> optionalIndices = new HashSet<>(list.optionalIndices());
    ImmutableList.Builder<CelMutableExpr> updatedElemBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<Integer> updatedIndicesBuilder = new ImmutableList.Builder<>();
    int newOptIndex = -1;
    for (int i = 0; i < list.elements().size(); i++) {
      newOptIndex++;
      CelMutableExpr element = list.elements().get(i);
      if (!optionalIndices.contains(i)) {
        updatedElemBuilder.add(element);
        continue;
      }

      if (element.getKind().equals(Kind.CALL)) {
        CelMutableCall call = element.call();
        if (call.function().equals(Function.OPTIONAL_NONE.getFunction())) {
          // Skip optional.none.
          // Skipping causes the list to get smaller.
          newOptIndex--;
          continue;
        } else if (call.function().equals(Function.OPTIONAL_OF.getFunction())) {
          CelMutableExpr arg = call.args().get(0);
          if (arg.getKind().equals(Kind.CONSTANT)) {
            updatedElemBuilder.add(call.args().get(0));
            continue;
          }
        }
      }

      updatedElemBuilder.add(element);
      updatedIndicesBuilder.add(newOptIndex);
    }

    return astMutator.replaceSubtree(
        mutableAst,
        CelMutableExpr.ofList(
            CelMutableList.create(updatedElemBuilder.build(), updatedIndicesBuilder.build())),
        expr.id());
  }

  private CelMutableAst pruneOptionalMapElements(CelMutableAst ast, CelMutableExpr expr) {
    CelMutableMap map = expr.map();
    ImmutableList.Builder<CelMutableMap.Entry> updatedEntryBuilder = new ImmutableList.Builder<>();
    boolean modified = false;
    for (CelMutableMap.Entry entry : map.entries()) {
      CelMutableExpr key = entry.key();
      Kind keyKind = key.getKind();
      CelMutableExpr value = entry.value();
      Kind valueKind = value.getKind();
      if (!entry.optionalEntry()
          || !keyKind.equals(Kind.CONSTANT)
          || !valueKind.equals(Kind.CALL)) {
        updatedEntryBuilder.add(entry);
        continue;
      }

      CelMutableCall call = value.call();
      if (call.function().equals(Function.OPTIONAL_NONE.getFunction())) {
        // Skip the element. This is resolving an optional.none: ex {?1: optional.none()}.
        modified = true;
        continue;
      } else if (call.function().equals(Function.OPTIONAL_OF.getFunction())) {
        CelMutableExpr arg = call.args().get(0);
        if (arg.getKind().equals(Kind.CONSTANT)) {
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
          ast, CelMutableExpr.ofMap(CelMutableMap.create(updatedEntryBuilder.build())), expr.id());
    }

    return ast;
  }

  private CelMutableAst pruneOptionalStructElements(CelMutableAst ast, CelMutableExpr expr) {
    CelMutableStruct struct = expr.struct();
    ImmutableList.Builder<CelMutableStruct.Entry> updatedEntryBuilder =
        new ImmutableList.Builder<>();
    boolean modified = false;
    for (CelMutableStruct.Entry entry : struct.entries()) {
      CelMutableExpr value = entry.value();
      Kind valueKind = value.getKind();
      if (!entry.optionalEntry() || !valueKind.equals(Kind.CALL)) {
        // Preserve the entry as is
        updatedEntryBuilder.add(entry);
        continue;
      }

      CelMutableCall call = value.call();
      if (call.function().equals(Function.OPTIONAL_NONE.getFunction())) {
        // Skip the element. This is resolving an optional.none: ex msg{?field: optional.none()}.
        modified = true;
        continue;
      } else if (call.function().equals(Function.OPTIONAL_OF.getFunction())) {
        CelMutableExpr arg = call.args().get(0);
        if (arg.getKind().equals(Kind.CONSTANT)) {
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
          ast,
          CelMutableExpr.ofStruct(
              CelMutableStruct.create(struct.messageName(), updatedEntryBuilder.build())),
          expr.id());
    }

    return ast;
  }

  @CanIgnoreReturnValue
  private static Object evaluateExpr(Cel cel, CelExpr expr)
      throws CelValidationException, CelEvaluationException {
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.newParsedAst(expr, CelSource.newBuilder().build());
    ast = cel.check(ast).getAst();

    return cel.createProgram(ast).eval();
  }

  /** Options to configure how Constant Folding behave. */
  @AutoValue
  public abstract static class ConstantFoldingOptions {
    public abstract int maxIterationLimit();

    public abstract ImmutableSet<String> foldableFunctions();

    /** Builder for configuring the {@link ConstantFoldingOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {

      abstract ImmutableSet.Builder<String> foldableFunctionsBuilder();

      /**
       * Limit the number of iteration while performing constant folding. An exception is thrown if
       * the iteration count exceeds the set value.
       */
      public abstract Builder maxIterationLimit(int value);

      /**
       * Adds a collection of custom functions that will be a candidate for constant folding. By
       * default, standard functions are foldable.
       *
       * <p>Note that the implementation of custom functions must be free of side effects.
       */
      @CanIgnoreReturnValue
      public Builder addFoldableFunctions(Iterable<String> functions) {
        checkNotNull(functions);
        this.foldableFunctionsBuilder().addAll(functions);
        return this;
      }

      /** See {@link #addFoldableFunctions(Iterable)}. */
      @CanIgnoreReturnValue
      public Builder addFoldableFunctions(String... functions) {
        return addFoldableFunctions(Arrays.asList(functions));
      }

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

  private static boolean isExprConstantOfKind(CelMutableExpr expr, CelConstant.Kind constantKind) {
    return expr.getKind().equals(Kind.CONSTANT) && expr.constant().getKind().equals(constantKind);
  }

  private ConstantFoldingOptimizer(ConstantFoldingOptions constantFoldingOptions) {
    this.constantFoldingOptions = constantFoldingOptions;
    this.astMutator = AstMutator.newInstance(constantFoldingOptions.maxIterationLimit());
    this.foldableFunctions =
        ImmutableSet.<String>builder()
            .addAll(DefaultOptimizerConstants.CEL_CANONICAL_FUNCTIONS)
            .addAll(constantFoldingOptions.foldableFunctions())
            .build();
  }
}
