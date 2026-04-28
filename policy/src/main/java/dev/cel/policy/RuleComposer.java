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

package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.CelMutableSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.Operator;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.formats.ValueString;
import dev.cel.common.navigation.CelNavigableMutableAst;
import dev.cel.common.navigation.CelNavigableMutableExpr;
import dev.cel.extensions.CelOptionalLibrary.Function;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.OutputValue;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Package-private class for composing various rules into a single expression using optimizer. */
final class RuleComposer implements CelAstOptimizer {
  private final CelCompiledRule compiledRule;
  private final String variablePrefix;
  private final AstMutator astMutator;

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) {
    Step result = optimizeRule(cel, compiledRule);
    return OptimizationResult.create(result.expr.toParsedAst());
  }

  private Step optimizeRule(Cel cel, CelCompiledRule compiledRule) {
    cel =
        cel.toCelBuilder()
            .addVarDeclarations(
                compiledRule.variables().stream()
                    .map(CelCompiledVariable::celVarDecl)
                    .collect(toImmutableList()))
            .build();

    Step output = null;
    // If the rule has an optional output, the last result in the ternary should return
    // `optional.none`. This output is implicit and created here to reflect the desired
    // last possible output of this type of rule.
    if (compiledRule.hasOptionalOutput()) {
      output =
          Step.newUnconditionalOptionalStep(
              newTrueLiteral(), astMutator.newGlobalCall(Function.OPTIONAL_NONE.getFunction()));
    }

    long lastOutputId = 0;
    for (CelCompiledMatch match : Lists.reverse(compiledRule.matches())) {
      CelAbstractSyntaxTree conditionAst = match.condition();
      boolean isTriviallyTrue = match.isConditionTriviallyTrue();
      CelMutableAst condAst = CelMutableAst.fromCelAst(conditionAst);

      switch (match.result().kind()) {
        case OUTPUT:
          // If the match has an output, then it is considered a non-optional output since
          // it is explicitly stated. If the rule itself is optional, then the base case value
          // of output being optional.none() will convert the non-optional value to an optional
          // one.
          OutputValue matchOutput = match.result().output();
          CelMutableAst outAst = CelMutableAst.fromCelAst(matchOutput.ast());
          Step step = Step.newNonOptionalStep(!isTriviallyTrue, condAst, outAst);
          output = combine(astMutator, step, output);

          assertComposedAstIsValid(
              cel,
              output.expr,
              "conflicting output types found.",
              matchOutput.sourceId(),
              lastOutputId);
          lastOutputId = matchOutput.sourceId();
          break;
        case RULE:
          // If the match has a nested rule, then compute the rule and whether it has
          // an optional return value.
          CelCompiledRule matchNestedRule = match.result().rule();
          Step nestedRule = optimizeRule(cel, matchNestedRule);
          boolean nestedHasOptional = matchNestedRule.hasOptionalOutput();

          Step ruleStep =
              nestedHasOptional
                  ? Step.newOptionalStep(!isTriviallyTrue, condAst, nestedRule.expr)
                  : Step.newNonOptionalStep(!isTriviallyTrue, condAst, nestedRule.expr);
          output = combine(astMutator, ruleStep, output);

          assertComposedAstIsValid(
              cel,
              output.expr,
              String.format(
                  "failed composing the subrule '%s' due to conflicting output types.",
                  matchNestedRule.ruleId().map(ValueString::value).orElse("")),
              lastOutputId);
          break;
      }
    }

    CelMutableAst resultExpr = output.expr;
    resultExpr = inlineCompiledVariables(resultExpr, compiledRule.variables());
    resultExpr = astMutator.renumberIdsConsecutively(resultExpr);

    return output.isOptional
        ? Step.newUnconditionalOptionalStep(newTrueLiteral(), resultExpr)
        : Step.newUnconditionalNonOptionalStep(newTrueLiteral(), resultExpr);
  }

  static RuleComposer newInstance(
      CelCompiledRule compiledRule, String variablePrefix, int iterationLimit) {
    return new RuleComposer(compiledRule, variablePrefix, iterationLimit);
  }

  // Assembles two output expressions into a single output step.
  private Step combine(AstMutator astMutator, Step currentStep, Step accumulatedStep) {
    if (accumulatedStep == null) {
      return currentStep;
    }
    CelMutableAst trueCondition = newTrueLiteral();

    if (currentStep.isOptional) {
      return combineWhenCurrentIsOptional(currentStep, accumulatedStep, astMutator, trueCondition);
    } else {
      return combineWhenCurrentIsNonOptional(
          currentStep, accumulatedStep, astMutator, trueCondition);
    }
  }

  private Step combineWhenCurrentIsOptional(
      Step currentStep, Step accumulatedStep, AstMutator astMutator, CelMutableAst trueCondition) {
    // optional.combine(optional) // optional
    // (optional && conditional).combine(non-optional) // optional
    // (optional && unconditional).combine(non-optional) // non-optional
    if (accumulatedStep.isOptional) {
      if (currentStep.isConditional) {
        return Step.newUnconditionalOptionalStep(
            trueCondition,
            astMutator.newGlobalCall(
                Operator.CONDITIONAL.getFunction(),
                currentStep.cond,
                currentStep.expr,
                accumulatedStep.expr));
      } else {
        if (!isOptionalNone(accumulatedStep.expr)) {
          // If either the nested rule or current condition output are optional then
          // use optional.or() to specify the combination of the first and second results
          // Note, the argument order is reversed due to the traversal of matches in
          // reverse order.
          return Step.newUnconditionalOptionalStep(
              trueCondition,
              astMutator.newMemberCall(currentStep.expr, "or", accumulatedStep.expr));
        }
        return currentStep;
      }
    } else { // accumulatedStep is non-optional
      if (currentStep.isConditional) {
        return Step.newUnconditionalOptionalStep(
            trueCondition,
            astMutator.newGlobalCall(
                Operator.CONDITIONAL.getFunction(),
                currentStep.cond,
                currentStep.expr,
                astMutator.newGlobalCall(
                    Function.OPTIONAL_OF.getFunction(), accumulatedStep.expr)));
      } else {
        return Step.newUnconditionalNonOptionalStep(
            trueCondition,
            astMutator.newMemberCall(currentStep.expr, "orValue", accumulatedStep.expr));
      }
    }
  }

  private Step combineWhenCurrentIsNonOptional(
      Step currentStep, Step accumulatedStep, AstMutator astMutator, CelMutableAst trueCondition) {
    // non-optional.combine(non-optional) // non-optional
    // (non-optional && conditional).combine(optional) // optional
    // (non-optional && unconditional).combine(optional) // non-optional
    //
    // The last combination case is unusual, but effectively it means that the non-optional value
    // prunes away
    // the potential optional output.
    if (accumulatedStep.isOptional) {
      if (currentStep.isConditional) {
        return Step.newUnconditionalOptionalStep(
            trueCondition,
            astMutator.newGlobalCall(
                Operator.CONDITIONAL.getFunction(),
                currentStep.cond,
                astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), currentStep.expr),
                accumulatedStep.expr));
      } else {
        // If the condition is trivially true, none of the matches in the rule causes the result
        // to become optional, and the rule is not the last match, then this will introduce
        // unreachable outputs or rules (pruning away 'accumulatedStep').
        return currentStep;
      }
    } else { // accumulatedStep is non-optional
      return Step.newUnconditionalNonOptionalStep(
          trueCondition,
          astMutator.newGlobalCall(
              Operator.CONDITIONAL.getFunction(),
              currentStep.cond,
              currentStep.expr,
              accumulatedStep.expr));
    }
  }

  private static boolean isOptionalNone(CelMutableAst ast) {
    CelMutableExpr expr = ast.expr();
    return expr.getKind().equals(Kind.CALL)
        && expr.call().function().equals("optional.none")
        && expr.call().args().isEmpty();
  }

  private static CelMutableAst newTrueLiteral() {
    return CelMutableAst.of(
        CelMutableExpr.ofConstant(CelConstant.ofValue(true)), CelMutableSource.newInstance());
  }

  private CelMutableAst inlineCompiledVariables(
      CelMutableAst ast, List<CelCompiledVariable> compiledVariables) {
    CelMutableAst mutatedAst = ast;
    for (CelCompiledVariable compiledVariable : Lists.reverse(compiledVariables)) {
      String variableName = variablePrefix + compiledVariable.name();
      ImmutableList<CelNavigableMutableExpr> exprsToReplace =
          CelNavigableMutableAst.fromAst(mutatedAst)
              .getRoot()
              .allNodes()
              .filter(
                  node ->
                      node.expr().getKind().equals(Kind.IDENT)
                          && node.expr().ident().name().equals(variableName))
              .collect(toImmutableList());

      for (CelNavigableMutableExpr expr : exprsToReplace) {
        CelMutableAst variableAst = CelMutableAst.fromCelAst(compiledVariable.ast());
        mutatedAst = astMutator.replaceSubtree(mutatedAst, variableAst, expr.id());
      }
    }

    return mutatedAst;
  }

  private void assertComposedAstIsValid(
      Cel cel, CelMutableAst composedAst, String failureMessage, Long... ids) {
    assertComposedAstIsValid(cel, composedAst, failureMessage, Arrays.asList(ids));
  }

  private void assertComposedAstIsValid(
      Cel cel, CelMutableAst composedAst, String failureMessage, List<Long> ids) {
    try {
      cel.check(composedAst.toParsedAst()).getAst();
    } catch (CelValidationException e) {
      ids = ids.stream().filter(id -> id > 0).collect(toCollection(ArrayList::new));
      throw new RuleCompositionException(failureMessage, e, ids);
    }
  }

  // Step represents an intermediate stage of rule and match expression composition.
  //
  // The CelCompiledRule and CelCompiledMatch types are meant to represent standalone tuples of
  // condition and output expressions, and have no notion of how the order of combination would
  // impact composition since composition rules may vary based on the policy execution semantic,
  // e.g. first-match versus logical-or, logical-and, or accumulation.
  private static class Step {
    /**
     * Indicates whether the output step has an optional result. Individual conditional attributes
     * are not optional; however, rules and subrules can have optional output.
     */
    private final boolean isOptional;

    /** True if the condition expression is not trivially true. */
    private final boolean isConditional;

    /** The condition associated with the output. */
    private final CelMutableAst cond;

    /** The output expression for the step. */
    private final CelMutableAst expr;

    private Step(
        boolean isOptional, boolean isConditional, CelMutableAst cond, CelMutableAst expr) {
      this.isOptional = isOptional;
      this.isConditional = isConditional;
      this.cond = cond;
      this.expr = expr;
    }

    private static Step newOptionalStep(
        boolean isConditional, CelMutableAst cond, CelMutableAst expr) {
      return new Step(/* isOptional= */ true, isConditional, cond, expr);
    }

    private static Step newNonOptionalStep(
        boolean isConditional, CelMutableAst cond, CelMutableAst expr) {
      return new Step(/* isOptional= */ false, isConditional, cond, expr);
    }

    private static Step newUnconditionalOptionalStep(
        CelMutableAst trueCondition, CelMutableAst expr) {
      return new Step(/* isOptional= */ true, /* isConditional= */ false, trueCondition, expr);
    }

    private static Step newUnconditionalNonOptionalStep(
        CelMutableAst trueCondition, CelMutableAst expr) {
      return new Step(/* isOptional= */ false, /* isConditional= */ false, trueCondition, expr);
    }
  }

  static final class RuleCompositionException extends RuntimeException {
    final String failureReason;
    final List<Long> errorIds;
    final CelValidationException compileException;

    private RuleCompositionException(
        String failureReason, CelValidationException e, List<Long> errorIds) {
      super(e);
      this.failureReason = failureReason;
      this.errorIds = errorIds;
      this.compileException = e;
    }
  }

  private RuleComposer(CelCompiledRule compiledRule, String variablePrefix, int iterationLimit) {
    this.compiledRule = checkNotNull(compiledRule);
    this.variablePrefix = variablePrefix;
    this.astMutator = AstMutator.newInstance(iterationLimit);
  }
}
