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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSource;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.formats.ValueString;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.Result;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.Result.Kind;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;
import dev.cel.policy.CelPolicy.Import;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Variable;
import dev.cel.policy.RuleComposer.RuleCompositionException;
import dev.cel.validator.CelAstValidator;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;
import dev.cel.validator.validators.AstDepthLimitValidator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Package-private implementation for policy compiler. */
final class CelPolicyCompilerImpl implements CelPolicyCompiler {
  private static final String DEFAULT_VARIABLE_PREFIX = "variables.";
  private static final int DEFAULT_ITERATION_LIMIT = 1000;
  private final Cel cel;
  private final String variablesPrefix;
  private final int iterationLimit;
  private final Optional<CelAstValidator> astDepthValidator;

  @Override
  public CelCompiledRule compileRule(CelPolicy policy) throws CelPolicyValidationException {
    CompilerContext compilerContext = new CompilerContext(policy.policySource());

    Cel extendedCel = this.cel;

    if (!policy.imports().isEmpty()) {
      CelContainer.Builder containerBuilder =
          extendedCel.toCheckerBuilder().container().toBuilder();

      for (Import imp : policy.imports()) {
        try {
          containerBuilder.addAbbreviations(imp.name().value());
        } catch (IllegalArgumentException e) {
          compilerContext.addIssue(
              imp.id(),
              CelIssue.formatError(
                  1, 0, String.format("Error configuring import: %s", e.getMessage())));
        }
      }

      extendedCel = extendedCel.toCelBuilder().setContainer(containerBuilder.build()).build();
    }

    CelCompiledRule compiledRule = compileRuleImpl(policy.rule(), extendedCel, compilerContext);
    if (compilerContext.hasError()) {
      throw new CelPolicyValidationException(compilerContext.getIssueString());
    }

    return compiledRule;
  }

  @Override
  public CelAbstractSyntaxTree compose(CelPolicy policy, CelCompiledRule compiledRule)
      throws CelPolicyValidationException {
    Cel cel = compiledRule.cel();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(
                RuleComposer.newInstance(compiledRule, variablesPrefix, iterationLimit))
            .build();

    CelAbstractSyntaxTree ast;

    try {
      // This is a minimal expression used as a basis of stitching together all the rules into a
      // single graph.
      ast = cel.compile("true").getAst();
      ast = optimizer.optimize(ast);
    } catch (CelValidationException | CelOptimizationException e) {
      if (e.getCause() instanceof RuleCompositionException) {
        RuleCompositionException re = (RuleCompositionException) e.getCause();
        CompilerContext compilerContext = new CompilerContext(policy.policySource());
        // The exact CEL error message produced from composition failure isn't too useful for users.
        // Ex: ERROR: :1:1: found no matching overload for '_?_:_' applied to '(bool, map(int, int),
        // bool)' (candidates: (bool, %A0, %A0))
        // Transform the error messages in a user-friendly way while retaining the original
        // CelValidationException as its originating cause.

        ImmutableList<CelIssue> transformedIssues =
            re.compileException.getErrors().stream()
                .map(x -> CelIssue.formatError(x.getSourceLocation(), re.failureReason))
                .collect(toImmutableList());
        for (long id : re.errorIds) {
          compilerContext.addIssue(id, transformedIssues);
        }

        throw new CelPolicyValidationException(compilerContext.getIssueString(), re.getCause());
      }

      // Something has gone seriously wrong.
      throw new CelPolicyValidationException("Unexpected error while composing rules.", e);
    }

    assertAstDepthIsSafe(ast, cel);

    return ast;
  }

  private void assertAstDepthIsSafe(CelAbstractSyntaxTree ast, Cel cel)
      throws CelPolicyValidationException {
    if (!astDepthValidator.isPresent()) {
      return;
    }
    CelValidator celValidator =
        CelValidatorFactory.standardCelValidatorBuilder(cel)
            .addAstValidators(astDepthValidator.get())
            .build();
    CelValidationResult result = celValidator.validate(ast);
    if (result.hasError()) {
      throw new CelPolicyValidationException(result.getErrorString());
    }
  }

  private CelCompiledRule compileRuleImpl(
      CelPolicy.Rule rule, Cel ruleCel, CompilerContext compilerContext) {
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
        // A sentinel AST representing an error is created to allow compiler checks to continue
        varAst = newErrorAst();
      }
      String variableName = variable.name().value();
      CelVarDecl newVariable =
          CelVarDecl.newVarDeclaration(variablesPrefix + variableName, outputType);
      ruleCel = ruleCel.toCelBuilder().addVarDeclarations(newVariable).build();
      variableBuilder.add(CelCompiledVariable.create(variableName, varAst, newVariable));
    }

    ImmutableList.Builder<CelCompiledMatch> matchBuilder = ImmutableList.builder();
    for (Match match : rule.matches()) {
      CelAbstractSyntaxTree conditionAst;
      try {
        conditionAst = ruleCel.compile(match.condition().value()).getAst();
        if (!conditionAst.getResultType().equals(SimpleType.BOOL)) {
          compilerContext.addIssue(
              match.condition().id(),
              CelIssue.formatError(1, 0, "condition must produce a boolean output."));
        }
      } catch (CelValidationException e) {
        compilerContext.addIssue(match.condition().id(), e.getErrors());
        continue;
      }

      Result matchResult;
      switch (match.result().kind()) {
        case OUTPUT:
          CelAbstractSyntaxTree outputAst;
          ValueString output = match.result().output();
          try {
            outputAst = ruleCel.compile(output.value()).getAst();
          } catch (CelValidationException e) {
            compilerContext.addIssue(output.id(), e.getErrors());
            continue;
          }

          matchResult = Result.ofOutput(output.id(), outputAst);
          break;
        case RULE:
          CelCompiledRule nestedRule =
              compileRuleImpl(match.result().rule(), ruleCel, compilerContext);
          matchResult = Result.ofRule(nestedRule);
          break;
        default:
          throw new IllegalArgumentException("Unexpected kind: " + match.result().kind());
      }

      matchBuilder.add(CelCompiledMatch.create(match.id(), conditionAst, matchResult));
    }

    CelCompiledRule compiledRule =
        CelCompiledRule.create(
            rule.id(), rule.ruleId(), variableBuilder.build(), matchBuilder.build(), cel);

    // Validate that all branches in the policy are reachable
    checkUnreachableCode(compiledRule, compilerContext);

    return compiledRule;
  }

  private void checkUnreachableCode(CelCompiledRule compiledRule, CompilerContext compilerContext) {
    boolean ruleHasOptional = compiledRule.hasOptionalOutput();
    ImmutableList<CelCompiledMatch> compiledMatches = compiledRule.matches();
    int matchCount = compiledMatches.size();
    for (int i = matchCount - 1; i >= 0; i--) {
      CelCompiledMatch compiledMatch = compiledMatches.get(i);
      boolean isTriviallyTrue = compiledMatch.isConditionTriviallyTrue();

      if (isTriviallyTrue && !ruleHasOptional && i != matchCount - 1) {
        if (compiledMatch.result().kind().equals(Kind.OUTPUT)) {
          compilerContext.addIssue(
              compiledMatch.sourceId(),
              CelIssue.formatError(1, 0, "Match creates unreachable outputs"));
        } else {
          compilerContext.addIssue(
              compiledMatch.result().rule().sourceId(),
              CelIssue.formatError(1, 0, "Rule creates unreachable outputs"));
        }
      }
    }
  }

  private static CelAbstractSyntaxTree newErrorAst() {
    return CelAbstractSyntaxTree.newParsedAst(
        CelExpr.ofConstant(0, CelConstant.ofValue("*error*")), CelSource.newBuilder().build());
  }

  private static final class CompilerContext {
    private final ArrayList<CelIssue> issues;
    private final CelPolicySource celPolicySource;

    private void addIssue(long id, CelIssue... issues) {
      addIssue(id, Arrays.asList(issues));
    }

    private void addIssue(long id, List<CelIssue> issues) {
      for (CelIssue issue : issues) {
        CelSourceLocation absoluteLocation = computeAbsoluteLocation(id, issue);
        this.issues.add(CelIssue.formatError(absoluteLocation, issue.getMessage()));
      }
    }

    private CelSourceLocation computeAbsoluteLocation(long id, CelIssue issue) {
      int policySourceOffset =
          Optional.ofNullable(celPolicySource.getPositionsMap().get(id)).orElse(-1);
      if (policySourceOffset == -1) {
        return CelSourceLocation.NONE;
      }
      CelSourceLocation policySourceLocation =
          celPolicySource.getOffsetLocation(policySourceOffset).orElse(null);
      if (policySourceLocation == null) {
        return CelSourceLocation.NONE;
      }

      int absoluteLine = issue.getSourceLocation().getLine() + policySourceLocation.getLine() - 1;
      int absoluteColumn = issue.getSourceLocation().getColumn() + policySourceLocation.getColumn();
      int absoluteOffset = celPolicySource.getContent().lineOffsets().get(absoluteLine - 2);

      return celPolicySource
          .getOffsetLocation(absoluteOffset + absoluteColumn)
          .orElse(CelSourceLocation.NONE);
    }

    private boolean hasError() {
      return !issues.isEmpty();
    }

    private String getIssueString() {
      return CelIssue.toDisplayString(issues, celPolicySource);
    }

    private CompilerContext(CelPolicySource celPolicySource) {
      this.issues = new ArrayList<>();
      this.celPolicySource = celPolicySource;
    }
  }

  static final class Builder implements CelPolicyCompilerBuilder {
    private final Cel cel;
    private String variablesPrefix;
    private int iterationLimit;
    private Optional<CelAstValidator> astDepthLimitValidator;

    private Builder(Cel cel) {
      this.cel = cel;
      this.astDepthLimitValidator = Optional.of(AstDepthLimitValidator.DEFAULT);
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setVariablesPrefix(String prefix) {
      this.variablesPrefix = checkNotNull(prefix);
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setIterationLimit(int iterationLimit) {
      this.iterationLimit = iterationLimit;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public CelPolicyCompilerBuilder setAstDepthLimit(int astDepthLimit) {
      if (astDepthLimit < 0) {
        astDepthLimitValidator = Optional.empty();
      } else {
        astDepthLimitValidator = Optional.of(AstDepthLimitValidator.newInstance(astDepthLimit));
      }
      return this;
    }

    @Override
    public CelPolicyCompiler build() {
      return new CelPolicyCompilerImpl(
          cel, this.variablesPrefix, this.iterationLimit, astDepthLimitValidator);
    }
  }

  static Builder newBuilder(Cel cel) {
    return new Builder(cel)
        .setVariablesPrefix(DEFAULT_VARIABLE_PREFIX)
        .setIterationLimit(DEFAULT_ITERATION_LIMIT);
  }

  private CelPolicyCompilerImpl(
      Cel cel,
      String variablesPrefix,
      int iterationLimit,
      Optional<CelAstValidator> astDepthValidator) {
    this.cel = checkNotNull(cel);
    this.variablesPrefix = checkNotNull(variablesPrefix);
    this.iterationLimit = iterationLimit;
    this.astDepthValidator = astDepthValidator;
  }
}
