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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelConstant.Kind;
import dev.cel.extensions.CelOptionalLibrary.Function;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.parser.Operator;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;
import java.util.List;

/** Package-private class for composing various rules into a single expression using optimizer. */
final class RuleComposer implements CelAstOptimizer {
  private final CelCompiledRule compiledRule;
  private final ImmutableList<CelVarDecl> newVarDecls;
  private final String variablePrefix;
  private final AstMutator astMutator;

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) {
    RuleOptimizationResult result = optimizeRule(compiledRule);
    return OptimizationResult.create(result.ast().toParsedAst(), newVarDecls, ImmutableList.of());
  }

  @AutoValue
  abstract static class RuleOptimizationResult {
    abstract CelMutableAst ast();

    abstract Boolean isOptionalResult();

    static RuleOptimizationResult create(CelMutableAst ast, boolean isOptionalResult) {
      return new AutoValue_RuleComposer_RuleOptimizationResult(ast, isOptionalResult);
    }
  }

  private RuleOptimizationResult optimizeRule(CelCompiledRule compiledRule) {
    CelMutableAst matchAst = astMutator.newGlobalCall(Function.OPTIONAL_NONE.getFunction());
    boolean isOptionalResult = true;
    for (CelCompiledMatch match : Lists.reverse(compiledRule.matches())) {
      CelAbstractSyntaxTree conditionAst = match.condition();
      boolean isTriviallyTrue =
          conditionAst.getExpr().constantOrDefault().getKind().equals(Kind.BOOLEAN_VALUE)
              && conditionAst.getExpr().constant().booleanValue();
      switch (match.result().kind()) {
        case OUTPUT:
          CelMutableAst outAst = CelMutableAst.fromCelAst(match.result().output());
          if (isTriviallyTrue) {
            matchAst = outAst;
            isOptionalResult = false;
            continue;
          }
          if (isOptionalResult) {
            outAst = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), outAst);
          }

          matchAst =
              astMutator.newGlobalCall(
                  Operator.CONDITIONAL.getFunction(),
                  CelMutableAst.fromCelAst(conditionAst),
                  outAst,
                  matchAst);
          continue;
        case RULE:
          RuleOptimizationResult nestedRule = optimizeRule(match.result().rule());
          CelMutableAst nestedRuleAst = nestedRule.ast();
          if (isOptionalResult && !nestedRule.isOptionalResult()) {
            nestedRuleAst =
                astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), nestedRuleAst);
          }
          if (!isOptionalResult && nestedRule.isOptionalResult()) {
            matchAst = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), matchAst);
            isOptionalResult = true;
          }
          if (!isOptionalResult && !nestedRule.isOptionalResult()) {
            throw new IllegalArgumentException("Subrule early terminates policy");
          }
          matchAst = astMutator.newMemberCall(nestedRuleAst, Function.OR.getFunction(), matchAst);
          break;
      }
    }

    CelMutableAst result = matchAst;
    for (CelCompiledVariable variable : Lists.reverse(compiledRule.variables())) {
      result =
          astMutator.replaceSubtreeWithNewBindMacro(
              result,
              variablePrefix + variable.name(),
              CelMutableAst.fromCelAst(variable.ast()),
              result.expr(),
              result.expr().id(),
              true);
    }

    result = astMutator.renumberIdsConsecutively(result);

    return RuleOptimizationResult.create(result, isOptionalResult);
  }

  static RuleComposer newInstance(
      CelCompiledRule compiledRule,
      List<CelVarDecl> newVarDecls,
      String variablePrefix,
      int iterationLimit) {
    return new RuleComposer(compiledRule, newVarDecls, variablePrefix, iterationLimit);
  }

  private RuleComposer(
      CelCompiledRule compiledRule,
      List<CelVarDecl> newVarDecls,
      String variablePrefix,
      int iterationLimit) {
    this.compiledRule = checkNotNull(compiledRule);
    this.newVarDecls = ImmutableList.copyOf(checkNotNull(newVarDecls));
    this.variablePrefix = variablePrefix;
    this.astMutator = AstMutator.newInstance(iterationLimit);
  }
}
