package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.CelMutableSource;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelConstant.Kind;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExprConverter;
import dev.cel.extensions.CelOptionalLibrary.Function;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.parser.Operator;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;
import java.util.List;

final class RuleComposer implements CelAstOptimizer {
    private final CelCompiledRule compiledRule;
  private final ImmutableList<CelVarDecl> newVarDecls;
  private final AstMutator astMutator;

    @Override
    public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) throws CelOptimizationException {
      RuleOptimizationResult result = optimizeRule(CelMutableAst.fromCelAst(ast), compiledRule);
      return OptimizationResult.create(
          result.ast().toParsedAst(),
          newVarDecls,
          ImmutableList.of()
      );
    }

    @AutoValue
    static abstract class RuleOptimizationResult {
      abstract CelMutableAst ast();
      abstract Boolean isOptionalResult();

      static RuleOptimizationResult create(CelMutableAst ast, boolean isOptionalResult) {
        return new AutoValue_RuleComposer_RuleOptimizationResult(ast, isOptionalResult);
      }
    }

  /**
   * TODO: Remove
   */
  private CelMutableAst newAst(CelMutableExpr expr) {
    return CelMutableAst.of(expr, CelMutableSource.newInstance());
  }

    private RuleOptimizationResult optimizeRule(CelMutableAst ast, CelCompiledRule compiledRule) {
      // CelMutableAst matchAst = astMutator.newGlobalCall(Function.OPTIONAL_NONE.getFunction());
      CelMutableAst matchAst = CelMutableAst.of(CelMutableExpr.ofCall(1, CelMutableCall.create(Function.OPTIONAL_NONE.getFunction())), ast.source());
      boolean isOptionalResult = true;
      for (CelCompiledMatch match : Lists.reverse(compiledRule.matches())) { // TODO: check reverse
        CelExpr conditionExpr = match.condition().getExpr();
        boolean isTriviallyTrue = conditionExpr.constantOrDefault().getKind().equals(Kind.BOOLEAN_VALUE) &&
            conditionExpr.constant().booleanValue();
        switch (match.result().kind()) {
          case OUTPUT:
            CelMutableExpr outExpr = CelMutableExprConverter.fromCelExpr(match.result().output().getExpr());
            if (isTriviallyTrue) {
              matchAst = newAst(outExpr);
              isOptionalResult = false;
              continue;
            }
            if (isOptionalResult) {
              outExpr = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), outExpr);
            }

            matchAst = newAst(astMutator.newGlobalCall(
                Operator.CONDITIONAL.getFunction(),
                CelMutableExprConverter.fromCelExpr(conditionExpr),
                outExpr,
                matchAst.expr())
            );
            continue;
          case RULE:
            RuleOptimizationResult nestedRule = optimizeRule(ast, match.result().rule());
            CelMutableAst nestedRuleAst = nestedRule.ast();
            if (isOptionalResult && !nestedRule.isOptionalResult()) {
              nestedRuleAst = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), nestedRuleAst);
            }
            if (!isOptionalResult && nestedRule.isOptionalResult()) {
              matchAst = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), matchAst);
              isOptionalResult = true;
            }
            if (!isOptionalResult && !nestedRule.isOptionalResult()) {
              // TODO: Report error
              throw new IllegalArgumentException("Subrule early terminates policy");
            }
            matchAst = astMutator.newMemberCall(nestedRuleAst, Function.OR.getFunction(), matchAst);
            break;
        }
      }

      CelMutableAst result = matchAst;
      for (CelCompiledVariable variable : Lists.reverse(compiledRule.variables())) {
        result = astMutator.replaceSubtreeWithNewBindMacro(
            result,
            "variables." + variable.name(), // TODO: Accept prefix
            CelMutableAst.fromCelAst(variable.ast()),
            result.expr(),
            result.expr().id(),
            true
            );
      }

      result = astMutator.renumberIdsConsecutively(result);

      return RuleOptimizationResult.create(result, isOptionalResult);
    }

    static RuleComposer newInstance(CelCompiledRule compiledRule, List<CelVarDecl> newVarDecls) {
      return new RuleComposer(compiledRule, newVarDecls);
    }

    private RuleComposer(CelCompiledRule compiledRule, List<CelVarDecl> newVarDecls) {
      this.compiledRule = checkNotNull(compiledRule);
      this.newVarDecls = ImmutableList.copyOf(checkNotNull(newVarDecls)); // TODO: This is fine, but we can also derive while composing here. Evaluate which method is better.
      this.astMutator = AstMutator.newInstance(10000); // TODO: Configurable
    }
  }
