package dev.cel.policy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExprFactory;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;

public final class CelPolicyCompilerImpl implements CelPolicyCompiler {

  private final Cel cel;

  @Override
  public CelAbstractSyntaxTree compile(CelPolicy policy) {
    CelExpr celExpr = CelExprFactory.newInstance().newBoolLiteral(true);

    CelAbstractSyntaxTree ast = CelAbstractSyntaxTree.newParsedAst(celExpr, CelSource.newBuilder().build());
  }

  private CompiledRule compileRule(CelPolicy.Rule rule) {
    return null;
  }

  @AutoValue
  private static abstract class CompiledRule {
    abstract ImmutableList<CompiledVariable> variables();
    abstract ImmutableList<CompiledMatch> matches();

    @AutoValue
    private static abstract class CompiledVariable {
      abstract String name();
      abstract CelAbstractSyntaxTree ast();
    }

    @AutoValue
    private static abstract class CompiledMatch {
      abstract CelAbstractSyntaxTree condition();
      abstract CelAbstractSyntaxTree output();
      abstract CompiledRule nestedRule();
    }
  }

  private static final class RuleComposer implements CelAstOptimizer {
    @Override
    public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) throws CelOptimizationException {
      return OptimizationResult.create(ast);
    }
  }

  static CelPolicyCompiler newInstance(Cel cel) {
    return new CelPolicyCompilerImpl(cel, CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(new RuleComposer())
            .build());
  }

  private CelPolicyCompilerImpl(Cel cel, CelOptimizer celOptimizer) {
    this.cel = cel;
  }
}

