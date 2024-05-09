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

import com.google.rpc.context.AttributeContext;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;

/**
 * Exercise8 demonstrates how to leverage canonical CEL validators to perform advanced validations
 * on an AST and CEL optimizers to improve evaluation efficiency.
 */
final class Exercise8 {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("x", SimpleType.INT)
          .addVar(
              "request", StructTypeReference.create("google.rpc.context.AttributeContext.Request"))
          .addMessageTypes(AttributeContext.Request.getDescriptor())
          .build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addMessageTypes(AttributeContext.Request.getDescriptor())
          .build();

  // Statically declare the validator and optimizer here.

  /**
   * Compiles the input expression.
   *
   * @throws CelValidationException If the expression contains parsing or type-checking errors.
   */
  CelAbstractSyntaxTree compile(String expression) throws CelValidationException {
    return CEL_COMPILER.compile(expression).getAst();
  }

  /** Validates a type-checked AST. */
  @SuppressWarnings("DoNotCallSuggester")
  CelValidationResult validate(CelAbstractSyntaxTree checkedAst) {
    throw new UnsupportedOperationException("To be implemented");
  }

  /** Optimizes a type-checked AST. */
  @SuppressWarnings("DoNotCallSuggester")
  CelAbstractSyntaxTree optimize(CelAbstractSyntaxTree checkedAst) {
    throw new UnsupportedOperationException("To be implemented");
  }

  /** Evaluates the compiled AST with the user provided parameter values. */
  Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues)
      throws CelEvaluationException {
    CelRuntime.Program program = CEL_RUNTIME.createProgram(ast);
    return program.eval(parameterValues);
  }
}
