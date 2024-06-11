package dev.cel.policy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.ast.CelConstant.Kind;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExprConverter;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.parser.Operator;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;

final class RuleComposer implements CelAstOptimizer {
    private final CelCompiledRule compiledRule;
    private final AstMutator astMutator;

    @Override
    public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) throws CelOptimizationException {
      return OptimizationResult.create(ast);
    }

    @AutoValue
    static abstract class RuleOptimizationResult {
      abstract CelMutableExpr expr();
      abstract Boolean isOptionalResult();

      // static RuleOptimizationResult create(CelMutableExpr expr, boolean isOptionalResult) {
      //   return new AutoValue_CelPolicyCompilerImpl_RuleComposer_RuleOptimizationResult(expr, isOptionalResult);
      // }
    }

    private CelAbstractSyntaxTree optimizeRule(CelAbstractSyntaxTree ast, CelCompiledRule compiledRule) {
      CelMutableExpr matchExpr = CelMutableExpr.ofCall(CelMutableCall.create("optional.none"));
      CelMutableAst astToModify = CelMutableAst.fromCelAst(ast);
      boolean isOptionalResult = true;
      for (CelCompiledMatch match : Lists.reverse(compiledRule.matches())) { // TODO: check reverse
        CelExpr conditionExpr = match.condition().getExpr();
        boolean isTriviallyTrue = conditionExpr.constantOrDefault().getKind().equals(Kind.BOOLEAN_VALUE) &&
            conditionExpr.constant().booleanValue();
        switch (match.result().kind()) {
          case OUTPUT:
            if (isTriviallyTrue) {
              matchExpr = CelMutableExprConverter.fromCelExpr(conditionExpr);
              isOptionalResult = false;
              continue;
            }
            CelMutableExpr outExpr = matchExpr;
            if (isOptionalResult) {
              outExpr = CelMutableExpr.ofCall(CelMutableCall.create("optional.of", outExpr));
            }

            matchExpr = CelMutableExpr.ofCall(
                CelMutableCall.create(
                  Operator.CONDITIONAL.getFunction(),
                    CelMutableExprConverter.fromCelExpr(conditionExpr),
                    outExpr,
                    matchExpr));
            break;
          case RULE:
            break;
        }
      }

      return null;
    }

    static RuleComposer newInstance(CelCompiledRule compiledRule) {
      return new RuleComposer(compiledRule);
    }

    private RuleComposer(CelCompiledRule compiledRule) {
      this.compiledRule = compiledRule;
      this.astMutator = AstMutator.newInstance(10000); // TODO: Configurable
    }
  }
