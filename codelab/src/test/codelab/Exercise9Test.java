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

package codelab;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.rpc.context.AttributeContext;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class Exercise9Test {
  private final Exercise9 exercise9 = new Exercise9();

  @Test
  public void validate_invalidHttpMethod_returnsError() throws Exception {
    String expression =
        "google.rpc.context.AttributeContext.Request { \n"
            + "scheme: 'http', "
            + "method: 'GETTT', " // method is misspelled.
            + "host: 'cel.dev' \n"
            + "}";
    CelAbstractSyntaxTree ast = exercise9.compile(expression);

    CelValidationResult validationResult = exercise9.validate(ast);

    assertThat(validationResult.hasError()).isTrue();
    assertThat(validationResult.getErrorString())
        .isEqualTo(
            "ERROR: <input>:2:25: GETTT is not an allowed HTTP method.\n"
                + " | scheme: 'http', method: 'GETTT', host: 'cel.dev' \n"
                + " | ........................^");
    assertThrows(CelValidationException.class, validationResult::getAst);
  }

  @Test
  public void validate_schemeIsHttp_returnsWarning() throws Exception {
    String expression =
        "google.rpc.context.AttributeContext.Request { \n"
            + "scheme: 'http', " // https is preferred but not required.
            + "method: 'GET', "
            + "host: 'cel.dev' \n"
            + "}";
    CelAbstractSyntaxTree ast = exercise9.compile(expression);

    CelValidationResult validationResult = exercise9.validate(ast);

    assertThat(validationResult.hasError()).isFalse();
    assertThat(validationResult.getIssueString())
        .isEqualTo(
            "WARNING: <input>:2:9: Prefer using https for safety.\n"
                + " | scheme: 'http', method: 'GET', host: 'cel.dev' \n"
                + " | ........^");
    // Because the validation result does not contain any errors, you can still evaluate it.
    assertThat(exercise9.eval(validationResult.getAst()))
        .isEqualTo(
            AttributeContext.Request.newBuilder()
                .setScheme("http")
                .setMethod("GET")
                .setHost("cel.dev")
                .build());
  }

  @Test
  public void validate_isPrimeNumberWithinMacro_returnsError() throws Exception {
    String expression = "[2,3,5].all(x, is_prime_number(x))";
    CelAbstractSyntaxTree ast = exercise9.compile(expression);

    CelValidationResult validationResult = exercise9.validate(ast);

    assertThat(validationResult.hasError()).isTrue();
    assertThat(validationResult.getErrorString())
        .isEqualTo(
            "ERROR: <input>:1:12: is_prime_number function cannot be used within CEL macros.\n"
                + " | [2,3,5].all(x, is_prime_number(x))\n"
                + " | ...........^");
  }
}
