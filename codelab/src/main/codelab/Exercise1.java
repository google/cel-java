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

/**
 * Exercise1 evaluates a simple literal expression: "Hello, World!"
 *
 * <p>Compile, eval, profit!
 */
final class Exercise1 {
  /**
   * Compile the input {@code expression} and produce an AST. This method parses and type-checks the
   * given expression to validate the syntax and type-agreement of the expression.
   *
   * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
   *     errors.
   */
  @SuppressWarnings("DoNotCallSuggester")
  CelAbstractSyntaxTree compile(String expression) {
    throw new UnsupportedOperationException("To be implemented");
  }

  /**
   * Evaluates the compiled AST.
   *
   * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
   */
  @SuppressWarnings("DoNotCallSuggester")
  Object eval(CelAbstractSyntaxTree ast) {
    throw new UnsupportedOperationException("To be implemented");
  }
}
