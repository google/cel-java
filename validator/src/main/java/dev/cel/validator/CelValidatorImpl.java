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

package dev.cel.validator;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue;
import dev.cel.common.CelValidationResult;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.validator.CelAstValidator.IssuesFactory;
import java.util.Arrays;

/**
 * {@link CelValidator} implementation to handle AST validation. An instance should be created using
 * {@link CelValidatorFactory}.
 */
final class CelValidatorImpl implements CelValidator {
  private final Cel cel;
  private final ImmutableSet<CelAstValidator> astValidators;

  CelValidatorImpl(Cel cel, ImmutableSet<CelAstValidator> astValidators) {
    this.cel = cel;
    this.astValidators = astValidators;
  }

  @Override
  public CelValidationResult validate(CelAbstractSyntaxTree ast) {
    if (!ast.isChecked()) {
      throw new IllegalArgumentException("AST must be type-checked.");
    }

    ImmutableList.Builder<CelIssue> issueBuilder = ImmutableList.builder();

    for (CelAstValidator validator : astValidators) {
      CelNavigableAst navigableAst = CelNavigableAst.fromAst(ast);
      IssuesFactory issuesFactory = new IssuesFactory(navigableAst);
      validator.validate(navigableAst, cel, issuesFactory);
      issueBuilder.addAll(issuesFactory.getIssues());
    }

    return new CelValidationResult(ast, issueBuilder.build());
  }

  /** Create a new builder for constructing a {@link CelValidator} instance. */
  static Builder newBuilder(Cel cel) {
    return new Builder(cel);
  }

  /** Builder class for {@link CelValidatorImpl}. */
  static final class Builder implements CelValidatorBuilder {
    private final Cel cel;
    private final ImmutableSet.Builder<CelAstValidator> astValidators;

    private Builder(Cel cel) {
      this.cel = cel;
      this.astValidators = ImmutableSet.builder();
    }

    @Override
    public CelValidatorBuilder addAstValidators(CelAstValidator... astValidators) {
      checkNotNull(astValidators);
      return addAstValidators(Arrays.asList(astValidators));
    }

    @Override
    public CelValidatorBuilder addAstValidators(Iterable<CelAstValidator> astValidators) {
      checkNotNull(astValidators);
      this.astValidators.addAll(astValidators);
      return this;
    }

    @Override
    public CelValidator build() {
      return new CelValidatorImpl(cel, astValidators.build());
    }
  }
}
