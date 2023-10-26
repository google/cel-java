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

package codelab.solutions;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;

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
  CelAbstractSyntaxTree compile(String expression) {
    // Construct a CelCompiler instance.
    // "String" is the expected output type for the type-checked expression
    // CelCompiler is immutable and when statically configured can be moved to a static final
    // member.
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().setResultType(SimpleType.STRING).build();

    CelAbstractSyntaxTree ast;
    try {
      // Parse the expression
      ast = celCompiler.parse(expression).getAst();
    } catch (CelValidationException e) {
      // Report syntactic errors, if present
      throw new IllegalArgumentException(
          "Failed to parse expression. Reason: " + e.getMessage(), e);
    }

    try {
      // Type-check the expression for correctness
      ast = celCompiler.check(ast).getAst();
    } catch (CelValidationException e) {
      // Report semantic errors, if present.
      throw new IllegalArgumentException(
          "Failed to type-check expression. Reason: " + e.getMessage(), e);
    }
    return ast;
  }

  /**
   * Evaluates the compiled AST.
   *
   * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
   */
  Object eval(CelAbstractSyntaxTree ast) {
    // Construct a CelRuntime instance
    // CelRuntime is immutable just like the compiler and can be moved to a static final member.
    CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

    try {
      // Plan the program
      CelRuntime.Program program = celRuntime.createProgram(ast);

      // Evaluate the program without any additional arguments.
      return program.eval();
    } catch (CelEvaluationException e) {
      // Report any evaluation errors, if present
      throw new IllegalArgumentException(
          "Evaluation error has occurred. Reason: " + e.getMessage(), e);
    }
  }
}
