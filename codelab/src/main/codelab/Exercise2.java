// Copyright 2022 Google LLC
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

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.CelType;
import java.util.Map;

/**
 * Exercise2 shows how to declare and use variables in expressions through two examples:
 *
 * <ul>
 *   <li>Given a user supplied integer value, test whether the value is negative
 *   <li>Given a request of type {@link com.google.rpc.context.AttributeContext.Request} determine
 *       whether a specific auth claim is set.
 * </ul>
 */
final class Exercise2 {

  /**
   * Compiles the input expression with provided variable information.
   *
   * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
   *     errors.
   */
  @SuppressWarnings("DoNotCallSuggester")
  CelAbstractSyntaxTree compile(String expression, String variableName, CelType variableType) {
    throw new UnsupportedOperationException("To be implemented");
  }

  /**
   * Evaluates the compiled AST with the user provided parameter values.
   *
   * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
   */
  @SuppressWarnings("DoNotCallSuggester")
  Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues) {
    throw new UnsupportedOperationException("To be implemented");
  }
}
