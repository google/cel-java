package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSource;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.Result;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Variable;
import java.util.ArrayList;
import java.util.List;

public final class CelPolicyCompilerImpl implements CelPolicyCompiler {

  private final Cel cel;
  private final String variablesPrefix;

  @Override
  public CelAbstractSyntaxTree compile(CelPolicy policy) throws CelPolicyValidationException {
    CompilerContext compilerContext = new CompilerContext(policy.celSource());
    CelCompiledRule compiledRule = compileRule(policy.rule(), cel, compilerContext);
    if (compilerContext.hasError()) {
      throw new CelPolicyValidationException(compilerContext.getIssueString());
    }

    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(compiledRule.cel())
        .addAstOptimizers(RuleComposer.newInstance(compiledRule, compilerContext.newVariableDeclarations))
        .build();

    CelAbstractSyntaxTree ast;

    try {
      ast = cel.compile("true").getAst();
      ast = optimizer.optimize(ast);
    } catch (CelValidationException | CelOptimizationException e) {
      throw new CelPolicyValidationException("Failed composing the rules", e);
    }

    return ast;
  }

  private CelCompiledRule compileRule(CelPolicy.Rule rule, Cel ruleCel, CompilerContext compilerContext) {
    ImmutableList.Builder<CelCompiledVariable> variableBuilder = ImmutableList.builder();
    for (Variable variable : rule.variables()) {
      ValueString expression = variable.expression();
      CelAbstractSyntaxTree varAst;
      CelType outputType = SimpleType.DYN;
      try {
        varAst = ruleCel.compile(expression.value()).getAst();
        outputType = varAst.getResultType();
      } catch (CelValidationException e) {
        compilerContext.addIssue(expression.id(), e.getErrors());
        varAst = newErrorAst();
      }
      String variableName = variable.name().value();
      CelVarDecl newVariable = CelVarDecl.newVarDeclaration(String.format("%s.%s", variablesPrefix, variableName), outputType);
      compilerContext.addNewVarDecl(newVariable);
      ruleCel = ruleCel.toCelBuilder().addVarDeclarations(newVariable).build();
      variableBuilder.add(CelCompiledVariable.create(variableName, varAst));
    }

    ImmutableList.Builder<CelCompiledMatch> matchBuilder = ImmutableList.builder();
    for (Match match : rule.matches()) {
      CelAbstractSyntaxTree conditionAst;
      try {
        conditionAst = ruleCel.compile(match.condition().value()).getAst();
      } catch (CelValidationException e) {
        compilerContext.addIssue(match.condition().id(), e.getErrors());
        continue;
      }

      Result matchResult;
      switch (match.result().kind()) {
        case OUTPUT:
          CelAbstractSyntaxTree outputAst;
          try {
            outputAst = ruleCel.compile(match.result().output().value()).getAst();
          } catch (CelValidationException e) {
            compilerContext.addIssue(match.result().output().id(), e.getErrors());
            continue;
          }

          matchResult = Result.ofOutput(outputAst);
          break;
        case RULE:
          CelCompiledRule nestedRule = compileRule(match.result().rule(), ruleCel, compilerContext);
          matchResult = Result.ofRule(nestedRule);
          break;
        default:
          throw new IllegalArgumentException("Unexpected kind: " + match.result().kind());
      }

      matchBuilder.add(CelCompiledMatch.create(conditionAst, matchResult));
    }

    return CelCompiledRule.create(variableBuilder.build(), matchBuilder.build(), cel);
  }

  private static CelAbstractSyntaxTree newErrorAst() {
    return CelAbstractSyntaxTree.newParsedAst(CelExpr.ofConstant(0, CelConstant.ofValue("*error*")), CelSource.newBuilder().build());
  }

  private final static class CompilerContext {
    private static final Joiner JOINER = Joiner.on('\n');
    private final ArrayList<CelIssue> issues;
    private final ArrayList<CelVarDecl> newVariableDeclarations;
    private final CelSource celSource;

    private void addIssue(long id, List<CelIssue> issues) {
      for (CelIssue issue : issues) {
        // Compute relative source
          int position = celSource.getPositionsMap().get(id) + issue.getSourceLocation().getColumn();
          CelSourceLocation loc = celSource.getOffsetLocation(position).orElse(CelSourceLocation.NONE);
          CelIssue newIssue = CelIssue.formatError(loc, issue.getMessage());
          System.out.println(newIssue.getMessage());
          this.issues.add(newIssue);
      }
    }

    private void addNewVarDecl(CelVarDecl newVarDecl) {
      newVariableDeclarations.add(newVarDecl);
    }

    private boolean hasError() {
      return !issues.isEmpty();
    }

    private String getIssueString() {
      return JOINER.join(
          issues.stream().map(iss -> iss.toDisplayString(celSource))
              .collect(toImmutableList()));
    }

    private CompilerContext(CelSource celSource) {
      this.issues = new ArrayList<>();
      this.newVariableDeclarations = new ArrayList<>();
      this.celSource = celSource;
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

