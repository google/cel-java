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
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue.Severity;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;
import java.text.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class TimestampLiteralValidatorTest {
  private static final CelOptions CEL_OPTIONS =
      CelOptions.current().enableTimestampEpoch(true).build();

  private static final Cel CEL = CelFactory.standardCelBuilder().setOptions(CEL_OPTIONS).build();

  private static final CelValidator CEL_VALIDATOR =
      CelValidatorFactory.standardCelValidatorBuilder(CEL)
          .addAstValidators(TimestampLiteralValidator.INSTANCE)
          .build();

  @Test
  @TestParameters("{source: timestamp(0)}")
  @TestParameters("{source: timestamp(1624124124)}")
  @TestParameters("{source: timestamp('2021-06-19T17:35:24Z')}")
  @TestParameters("{source: timestamp('1972-01-01T10:00:20.021-05:00')}")
  public void timestamp_validFormat(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(CEL.createProgram(ast).eval()).isInstanceOf(Timestamp.class);
  }

  @Test
  public void timestampsInCallArgument_validFormat() throws Exception {
    String source =
        "string(timestamp(1524124124)) + ':' + string(timestamp('2021-06-19T17:35:24Z'))";
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(CEL.createProgram(ast).eval()).isInstanceOf(String.class);
  }

  @Test
  public void timestamp_withVariable_noOp() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("str_var", SimpleType.STRING)
            .setOptions(CEL_OPTIONS)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("timestamp(str_var)").getAst();

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
        .contains("evaluation error: Failed to parse timestamp: invalid timestamp \"bad\"");
  }

  @Test
  public void timestamp_withFunction_noOp() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "testFunc",
                    newGlobalOverload("testFuncOverloadId", SimpleType.INT, SimpleType.STRING)))
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "testFuncOverloadId",
                    String.class,
                    stringArg -> {
                      try {
                        return Timestamps.parse(stringArg).getSeconds();
                      } catch (ParseException e) {
                        throw new RuntimeException(e);
                      }
                    }))
            .setOptions(CEL_OPTIONS)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("timestamp(testFunc('bad'))").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    // Static AST validation cannot handle functions. This expectedly contains no errors
    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> cel.createProgram(ast).eval());
    // However, the same AST fails on evaluation when the function dispatch fails.
    assertThat(e)
        .hasMessageThat()
        .contains("evaluation error: Function 'testFuncOverloadId' failed with arg(s) 'bad'");
  }

  @Test
  public void timestamp_invalidFormat() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("timestamp('bad')").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getAllIssues().get(0).getSeverity()).isEqualTo(Severity.ERROR);
    assertThat(result.getAllIssues().get(0).toDisplayString(ast.getSource()))
        .isEqualTo(
            "ERROR: <input>:1:11: Timestamp validation failed. Reason: evaluation error: Failed to"
                + " parse timestamp: invalid timestamp \"bad\"\n"
                + " | timestamp('bad')\n"
                + " | ..........^");
  }
}
