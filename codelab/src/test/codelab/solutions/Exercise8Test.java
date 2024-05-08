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

package codelab.solutions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.rpc.context.AttributeContext;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.runtime.CelEvaluationException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class Exercise8Test {

  private final Exercise8 exercise8 = new Exercise8();

  @Test
  public void validate_invalidTimestampLiteral_returnsError() throws Exception {
    CelAbstractSyntaxTree ast = exercise8.compile("timestamp('bad')");

    CelValidationResult validationResult = exercise8.validate(ast);

    assertThat(validationResult.hasError()).isTrue();
    assertThat(validationResult.getErrorString())
        .isEqualTo(
            "ERROR: <input>:1:11: timestamp validation failed. Reason: evaluation error: Failed to"
                + " parse timestamp: invalid timestamp \"bad\"\n"
                + " | timestamp('bad')\n"
                + " | ..........^");
  }

  @Test
  public void validate_invalidDurationLiteral_returnsError() throws Exception {
    CelAbstractSyntaxTree ast = exercise8.compile("duration('bad')");

    CelValidationResult validationResult = exercise8.validate(ast);

    assertThat(validationResult.hasError()).isTrue();
    assertThat(validationResult.getErrorString())
        .isEqualTo(
            "ERROR: <input>:1:10: duration validation failed. Reason: evaluation error: invalid"
                + " duration format\n"
                + " | duration('bad')\n"
                + " | .........^");
  }

  @Test
  public void validate_invalidRegexLiteral_returnsError() throws Exception {
    CelAbstractSyntaxTree ast = exercise8.compile("'text'.matches('**')");

    CelValidationResult validationResult = exercise8.validate(ast);

    assertThat(validationResult.hasError()).isTrue();
    assertThat(validationResult.getErrorString())
        .isEqualTo(
            "ERROR: <input>:1:16: Regex validation failed. Reason: Dangling meta character '*' near"
                + " index 0\n"
                + "**\n"
                + "^\n"
                + " | 'text'.matches('**')\n"
                + " | ...............^");
  }

  @Test
  public void validate_listHasMixedLiterals_throws() throws Exception {
    CelAbstractSyntaxTree ast = exercise8.compile("3 in [1, 2, '3']");

    // Note that `CelValidationResult` is the same result class used for the compilation path. This
    // means you could alternatively invoke `.getAst()` and handle `CelValidationException` as
    // usual.
    CelValidationResult validationResult = exercise8.validate(ast);

    CelValidationException e = assertThrows(CelValidationException.class, validationResult::getAst);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "ERROR: <input>:1:13: expected type 'int' but found 'string'\n"
                + " | 3 in [1, 2, '3']\n"
                + " | ............^");
  }

  @Test
  public void optimize_constantFold_success() throws Exception {
    CelUnparser celUnparser = CelUnparserFactory.newUnparser();
    CelAbstractSyntaxTree ast = exercise8.compile("(1 + 2 + 3 == x) && (x in [1, 2, x])");

    CelAbstractSyntaxTree optimizedAst = exercise8.optimize(ast);

    assertThat(celUnparser.unparse(optimizedAst)).isEqualTo("6 == x");
  }

  @Test
  public void optimize_constantFold_evaluateError() throws Exception {
    CelAbstractSyntaxTree ast =
        exercise8.compile("request.headers.referer == 'https://' + 'cel.dev'");
    CelAbstractSyntaxTree optimizedAst = exercise8.optimize(ast);
    ImmutableMap<String, AttributeContext.Request> runtimeParameters =
        ImmutableMap.of("request", AttributeContext.Request.getDefaultInstance());

    CelEvaluationException e1 =
        assertThrows(CelEvaluationException.class, () -> exercise8.eval(ast, runtimeParameters));
    CelEvaluationException e2 =
        assertThrows(
            CelEvaluationException.class, () -> exercise8.eval(optimizedAst, runtimeParameters));
    // Note that the errors below differ by their source position.
    assertThat(e1)
        .hasMessageThat()
        .contains("evaluation error at <input>:15: key 'referer' is not present in map.");
    assertThat(e2)
        .hasMessageThat()
        .contains("evaluation error at <input>:0: key 'referer' is not present in map.");
  }

  @Test
  public void optimize_commonSubexpressionElimination_success() throws Exception {
    CelUnparser celUnparser = CelUnparserFactory.newUnparser();
    CelAbstractSyntaxTree ast =
        exercise8.compile(
            "request.auth.claims.group == 'admin' || request.auth.claims.group == 'user'");

    CelAbstractSyntaxTree optimizedAst = exercise8.optimize(ast);

    assertThat(celUnparser.unparse(optimizedAst))
        .isEqualTo(
            "cel.@block([request.auth.claims.group], @index0 == \"admin\" || @index0 == \"user\")");
  }
}
