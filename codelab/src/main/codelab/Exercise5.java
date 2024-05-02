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

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;

/**
 * Exercise5 covers how to build complex objects as CEL literals.
 *
 * <p>Given the input variable "time", construct a JWT with an expiry of 5 minutes
 */
final class Exercise5 {

  /**
   * Compiles the input expression.
   *
   * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
   *     errors.
   */
  CelAbstractSyntaxTree compile(String expression) {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            // Declare the 'time' variable here as a Timestamp.
            .build();

    try {
      return celCompiler.compile(expression).getAst();
    } catch (CelValidationException e) {
      throw new IllegalArgumentException("Failed to compile expression.", e);
    }
  }

  /**
   * Evaluates the compiled AST with the user provided parameter values.
   *
   * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
   */
  Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues) {
    CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

    try {
      CelRuntime.Program program = celRuntime.createProgram(ast);
      return program.eval(parameterValues);
    } catch (CelEvaluationException e) {
      throw new IllegalArgumentException("Evaluation error has occurred.", e);
    }
  }

  /** Converts the evaluated result into a JSON string using protobuf's google.protobuf.Struct. */
  @SuppressWarnings("DoNotCallSuggester")
  String toJson(Map<String, Object> map) {
    throw new UnsupportedOperationException("To be implemented");
  }
}
