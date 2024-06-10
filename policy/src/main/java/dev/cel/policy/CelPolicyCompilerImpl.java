package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExprFactory;
import dev.cel.common.types.CelType;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Variable;
import dev.cel.policy.CelPolicyCompilerImpl.CompiledRule.CompiledMatch;
import dev.cel.policy.CelPolicyCompilerImpl.CompiledRule.CompiledMatch.Result;
import dev.cel.policy.CelPolicyCompilerImpl.CompiledRule.CompiledVariable;
import java.util.ArrayList;
import java.util.List;

public final class CelPolicyCompilerImpl implements CelPolicyCompiler {
  private static final Joiner JOINER = Joiner.on('\n');

  private final Cel cel;
  private final String variablesPrefix;

  private final static class CompilerContext {
    private final ArrayList<CelIssue> issues;

    private void addIssue(List<CelIssue> issues) {
      this.issues.addAll(issues);
    }

    private boolean hasError() {
      return !issues.isEmpty();
    }

    public String getIssueString() {
      return "error!";
      // return JOINER.join(
      //     issues.stream().map(iss -> iss.toDisplayString(source))
      //         .collect(toImmutableList()));
    }


    private CompilerContext() {
      this.issues = new ArrayList<>();
    }
  }

  @Override
  public CelAbstractSyntaxTree compile(CelPolicy policy) throws CelPolicyValidationException {
    CompilerContext compilerContext = new CompilerContext();
    CompiledRule compiledRule = compileRule(policy.rule(), cel, compilerContext);
    if (compilerContext.hasError()) {
      throw new CelPolicyValidationException(compilerContext.getIssueString());
    }

    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(compiledRule.cel()).addAstOptimizers(new RuleComposer(compiledRule)).build();

    CelAbstractSyntaxTree ast;

    try {
      ast = cel.compile("true").getAst();
      ast = optimizer.optimize(ast);
    } catch (CelValidationException | CelOptimizationException e) {
      throw new CelPolicyValidationException("Failed composing the rules", e);
    }

    return ast;
  }

  private CompiledRule compileRule(CelPolicy.Rule rule, Cel ruleCel, CompilerContext compilerContext) {
    ImmutableList.Builder<CompiledVariable> variableBuilder = ImmutableList.builder();
    for (Variable variable : rule.variables()) {
      // TODO: Compute relative source
      String expression = variable.expression().value();
      CelAbstractSyntaxTree varAst;
      CelType outputType;
      try {
        varAst = ruleCel.compile(expression).getAst();
      } catch (CelValidationException e) {
        compilerContext.addIssue(e.getErrors());
        // TODO: Fall back to dyn. Requires extending the environment with the var.
        throw new RuntimeException(e);
      }
      String variableName = variable.name().value();
      CelVarDecl newVariable = CelVarDecl.newVarDeclaration(String.format("%s.%s", variablesPrefix, variableName), varAst.getResultType());
      ruleCel = ruleCel.toCelBuilder().addVarDeclarations(newVariable).build();
      variableBuilder.add(CompiledVariable.create(variableName, varAst));
    }

    ImmutableList.Builder<CompiledMatch> matchBuilder = ImmutableList.builder();
    for (Match match : rule.matches()) {
      CelAbstractSyntaxTree conditionAst;
      try {
        conditionAst = ruleCel.compile(match.condition().value()).getAst();
      } catch (CelValidationException e) {
        // TODO: Sentinel AST
        throw new RuntimeException(e);
      }

      Result matchResult;
      switch (match.result().kind()) {
        // TODO: Relative source
        case OUTPUT:
          CelAbstractSyntaxTree outputAst;
          try {
            outputAst = ruleCel.compile(match.result().output().value()).getAst();
          } catch (CelValidationException e) {
            // TODO: Sentinel AST
            throw new RuntimeException(e);
          }

          matchResult = Result.ofOutput(outputAst);
          break;
        case RULE:
          CompiledRule nestedRule = compileRule(match.result().rule(), ruleCel, compilerContext);
          matchResult = Result.ofRule(nestedRule);
          break;
        default:
          throw new IllegalArgumentException("Unexpected kind: " + match.result().kind());
      }

      matchBuilder.add(CompiledMatch.create(conditionAst, matchResult));
    }

    return CompiledRule.create(variableBuilder.build(), matchBuilder.build(), cel);
  }

  @AutoValue
  static abstract class CompiledRule {
    abstract ImmutableList<CompiledVariable> variables();
    abstract ImmutableList<CompiledMatch> matches();
    abstract Cel cel();

    @AutoValue
    static abstract class CompiledVariable {
      abstract String name();
      abstract CelAbstractSyntaxTree ast();

      static CompiledVariable create(String name, CelAbstractSyntaxTree ast) {
        return new AutoValue_CelPolicyCompilerImpl_CompiledRule_CompiledVariable(name, ast);
      }
    }

    @AutoValue
    static abstract class CompiledMatch {
      abstract CelAbstractSyntaxTree condition();
      abstract Result result();

      @AutoOneOf(CompiledMatch.Result.Kind.class)
      abstract static class Result {
        abstract CelAbstractSyntaxTree output();
        abstract CompiledRule rule();
        abstract Kind kind();

        static Result ofOutput(CelAbstractSyntaxTree value) {
          return AutoOneOf_CelPolicyCompilerImpl_CompiledRule_CompiledMatch_Result.output(value);
        }

        static Result ofRule(CompiledRule value) {
          return AutoOneOf_CelPolicyCompilerImpl_CompiledRule_CompiledMatch_Result.rule(value);
        }

        enum Kind {
          OUTPUT,
          RULE
        }
      }

      static CompiledMatch create(CelAbstractSyntaxTree condition, CompiledMatch.Result result) {
        return new AutoValue_CelPolicyCompilerImpl_CompiledRule_CompiledMatch(condition, result);
      }
    }

    static CompiledRule create(ImmutableList<CompiledVariable> variables, ImmutableList<CompiledMatch> matches, Cel cel) {
      return new AutoValue_CelPolicyCompilerImpl_CompiledRule(variables, matches, cel);
    }
  }

  private static final class RuleComposer implements CelAstOptimizer {
    private final CompiledRule compiledRule;
    @Override
    public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) throws CelOptimizationException {
      return OptimizationResult.create(ast);
    }

    private RuleComposer(CompiledRule compiledRule) {
      this.compiledRule = compiledRule;
    }
  }

  static final class Builder implements CelPolicyCompilerBuilder {
    private final Cel cel;
    private String variablesPrefix;

    private Builder(Cel cel) {
      this.cel = cel;
      this.variablesPrefix = "variables";
    }

    @CanIgnoreReturnValue
    Builder setVariablesPrefix(String prefix) {
      this.variablesPrefix = checkNotNull(prefix);
      return this;
    }

    @Override
    public CelPolicyCompiler build() {
      return new CelPolicyCompilerImpl(cel, this.variablesPrefix);
    }
  }

  static Builder newBuilder(Cel cel) {
    return new Builder(cel);
  }


  private CelPolicyCompilerImpl(Cel cel, String variablesPrefix) {
    this.cel = checkNotNull(cel);
    this.variablesPrefix = checkNotNull(variablesPrefix);
  }
}

