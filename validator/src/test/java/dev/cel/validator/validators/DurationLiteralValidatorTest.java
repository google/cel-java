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

package dev.cel.validator.validators;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Duration;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue.Severity;
import dev.cel.common.CelValidationResult;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;
import java.text.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class DurationLiteralValidatorTest {
  private static final Cel CEL = CelFactory.standardCelBuilder().build();

  private static final CelValidator CEL_VALIDATOR =
      CelValidatorFactory.standardCelValidatorBuilder(CEL)
          .addAstValidators(DurationLiteralValidator.INSTANCE)
          .build();

  @Test
  @TestParameters("{source: duration('0')}")
  @TestParameters("{source: duration('1h')}")
  @TestParameters("{source: duration('-1m6s')}")
  @TestParameters("{source: duration('2h3m4s5us')}")
  public void duration_validFormat(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(CEL.createProgram(ast).eval()).isInstanceOf(Duration.class);
  }

  @Test
  public void durationsInCallArgument_validFormat() throws Exception {
    String source = "string(duration('1h')) + ':' + string(duration('-1m'))";
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(CEL.createProgram(ast).eval()).isInstanceOf(String.class);
  }

  @Test
  public void duration_withVariable_noOp() throws Exception {
    Cel cel = CelFactory.standardCelBuilder().addVar("str_var", SimpleType.STRING).build();
    CelAbstractSyntaxTree ast = cel.compile("duration(str_var)").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    // Static AST validation cannot handle variables. This expectedly contains no errors
    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    // However, the same AST fails on evaluation when a bad variable is passed in.
    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class,
            () -> CEL.createProgram(ast).eval(ImmutableMap.of("str_var", "bad")));
    assertThat(e)
        .hasMessageThat()
        .contains("evaluation error at <input>:8: invalid duration format");
  }

  @Test
  public void duration_withFunction_noOp() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "testFunc",
                    newGlobalOverload("testFuncOverloadId", SimpleType.STRING, SimpleType.STRING)))
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "testFuncOverloadId",
                    String.class,
                    stringArg -> {
                      try {
                        return ProtoTimeUtils.parse(stringArg).toString();
                      } catch (ParseException e) {
                        throw new RuntimeException(e);
                      }
                    }))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("duration(testFunc('bad'))").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    // Static AST validation cannot handle functions. This expectedly contains no errors
    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> cel.createProgram(ast).eval());
    // However, the same AST fails on evaluation when the function dispatch fails.
    assertThat(e)
        .hasMessageThat()
        .contains(
            "evaluation error at <input>:17: Function 'testFuncOverloadId' failed with arg(s)"
                + " 'bad'");
  }

  @Test
  public void duration_invalidFormat() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("duration('bad')").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getAllIssues().get(0).getSeverity()).isEqualTo(Severity.ERROR);
    assertThat(result.getAllIssues().get(0).toDisplayString(ast.getSource()))
        .isEqualTo(
            "ERROR: <input>:1:10: duration validation failed. Reason: evaluation error: invalid"
                + " duration format\n"
                + " | duration('bad')\n"
                + " | .........^");
  }

  @Test
  public void duration_unexpectedResultType_throws() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .setStandardEnvironmentEnabled(false)
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "duration",
                    newGlobalOverload("duration_overload", SimpleType.DURATION, SimpleType.STRING)))
            .addFunctionBindings(
                CelFunctionBinding.from("duration_overload", String.class, (arg) -> 1))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("duration('1h')").getAst();
    CelValidator celValidator =
        CelValidatorFactory.standardCelValidatorBuilder(cel)
            .addAstValidators(DurationLiteralValidator.INSTANCE)
            .build();

    CelValidationResult result = celValidator.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getAllIssues().get(0).getSeverity()).isEqualTo(Severity.ERROR);
    assertThat(result.getAllIssues().get(0).toDisplayString(ast.getSource()))
        .isEqualTo(
            "ERROR: <input>:1:10: duration validation failed. Reason: Expected"
                + " com.google.protobuf.Duration type but got java.lang.Integer instead\n"
                + " | duration('1h')\n"
                + " | .........^");
  }

  @Test
  @TestParameters("{source: '{1: 2}'}")
  @TestParameters("{source: '[1,2,3]'}")
  public void parentIsNotCallExpr_doesNotThrow(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
  }
}
