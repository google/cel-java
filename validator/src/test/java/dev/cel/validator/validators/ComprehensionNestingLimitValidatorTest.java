// Copyright 2025 Google LLC
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

package dev.cel.validator.validators;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue.Severity;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelExtensions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ComprehensionNestingLimitValidatorTest {

  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .addCompilerLibraries(
              CelExtensions.optional(), CelExtensions.comprehensions(), CelExtensions.bindings())
          .addRuntimeLibraries(CelExtensions.optional(), CelExtensions.comprehensions())
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addVar("x", SimpleType.DYN)
          .build();

  private static final CelValidator CEL_VALIDATOR =
      CelValidatorFactory.standardCelValidatorBuilder(CEL)
          .addAstValidators(ComprehensionNestingLimitValidator.newInstance(1))
          .build();

  @Test
  public void comprehensionNestingLimit_populatesErrors(
      @TestParameter({
            "[1, 2, 3].map(x, [1, 2, 3].map(y, x + y))",
            "[1, 2, 3].map(x, [1, 2, 3].map(y, x + y).size() > 0, x)",
            "[1, 2, 3].all(x, [1, 2, 3].exists(y, x + y > 0))",
            "[1, 2, 3].map(x, {x: [1, 2, 3].map(y, x + y)})",
            "[1, 2, 3].exists(i, v, i < 3 && [1, 2, 3].all(j, v2, j < 3 && v2 > 0))",
            "{1: 2}.all(k, {2: 3}.all(k2, k != k2))"
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(expr).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getAllIssues().get(0).getSeverity()).isEqualTo(Severity.ERROR);
    assertThat(result.getAllIssues().get(0).toDisplayString(ast.getSource()))
        .contains("comprehension nesting exceeds the configured limit: 1.");
  }

  @Test
  public void comprehensionNestingLimit_accumulatesErrors(
      @TestParameter({
            "[1, 2, 3].map(x, [1, 2, 3].map(y, [1, 2, 3].map(z, x + y + z)))",
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(expr).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(2);
  }

  @Test
  public void comprehensionNestingLimit_limitConfigurable(
      @TestParameter({
            "[1, 2, 3].map(x, [1, 2, 3].map(y, x + y))",
            "[1, 2, 3].map(x, [1, 2, 3].map(y, x + y).size() > 0, x)",
            "[1, 2, 3].all(x, [1, 2, 3].exists(y, x + y > 0))",
            "[1, 2, 3].map(x, {x: [1, 2, 3].map(y, x + y)})",
            "[1, 2, 3].exists(i, v, i < 3 && [1, 2, 3].all(j, v2, j < 3 && v2 > 0))",
            "{1: 2}.all(k, {2: 3}.all(k2, k != k2))"
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(expr).getAst();
    CelValidator celValidator =
        CelValidatorFactory.standardCelValidatorBuilder(CEL)
            .addAstValidators(ComprehensionNestingLimitValidator.newInstance(2))
            .build();

    CelValidationResult result = celValidator.validate(ast);

    assertThat(result.hasError()).isFalse();
  }

  @Test
  public void comprehensionNestingLimit_trivialLoopsDontCount(
      @TestParameter({
            "cel.bind(x, [1, 2].map(x, x + 1), x + [1, 2].map(x, x + 1))",
            "optional.of(1).optMap(x, [1, 2, 3].exists(y, y == x))",
            "[].map(x, [1, 2, 3].map(y, x + y))",
            "{}.map(k1, {1: 2, 3: 4}.map(k2, k1 + k2))",
            "[1, 2, 3].map(x, cel.bind(y, 2, x + y))",
            "[1, 2, 3].map(x, optional.of(1).optMap(y, x + y).orValue(0))",
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(expr).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
  }

  @Test
  public void comprehensionNestingLimit_zeroLimitAcceptedComprehenions(
      @TestParameter({
            "cel.bind(x, 1, x + 1)",
            "optional.of(1).optMap(x, x + 1)",
            "[].map(x, int(x))",
            "cel.bind(x, 1 + [].map(x, int(x)).size(), x + 1)"
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(expr).getAst();

    CelValidator celValidator =
        CelValidatorFactory.standardCelValidatorBuilder(CEL)
            .addAstValidators(ComprehensionNestingLimitValidator.newInstance(0))
            .build();

    CelValidationResult result = celValidator.validate(ast);

    assertThat(result.hasError()).isFalse();
  }

  @Test
  public void comprehensionNestingLimit_zeroLimitRejectedComprehensions(
      @TestParameter({
            "[1].map(x, x)",
            "[1].exists(x, x > 0)",
            "[].exists(x, [1].all(y, y > 0))",
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(expr).getAst();

    CelValidator celValidator =
        CelValidatorFactory.standardCelValidatorBuilder(CEL)
            .addAstValidators(ComprehensionNestingLimitValidator.newInstance(0))
            .build();

    CelValidationException e =
        assertThrows(CelValidationException.class, () -> celValidator.validate(ast).getAst());

    assertThat(e.getMessage()).contains("comprehension nesting exceeds the configured limit: 0.");
  }
}
