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
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;

/**
 * Exercise9 demonstrates how to author a custom AST validator to perform domain specific
 * validations.
 *
 * <p>Given a `google.rpc.context.AttributeContext.Request` message, validate that its fields follow
 * the expected HTTP specification.
 *
 * <p>Given an expression containing an expensive function call, validate that it is not nested
 * within a macro.
 */
final class Exercise9 {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .setStandardMacros(CelStandardMacro.ALL)
          .addFunctionDeclarations(
              CelFunctionDecl.newFunctionDeclaration(
                  "is_prime_number",
                  CelOverloadDecl.newGlobalOverload(
                      "is_prime_number_int",
                      "Invokes an expensive RPC call to check if the value is a prime number.",
                      SimpleType.BOOL,
                      SimpleType.INT)))
          .addMessageTypes(AttributeContext.Request.getDescriptor())
          .build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addMessageTypes(AttributeContext.Request.getDescriptor())
          .build();
  private static final CelValidator CEL_VALIDATOR =
      CelValidatorFactory.standardCelValidatorBuilder(CEL_COMPILER, CEL_RUNTIME)
          // Add your custom AST validators here
          .build();

  /**
   * Compiles the input expression.
   *
   * @throws CelValidationException If the expression contains parsing or type-checking errors.
   */
  CelAbstractSyntaxTree compile(String expression) throws CelValidationException {
    return CEL_COMPILER.compile(expression).getAst();
  }

  /** Validates a type-checked AST. */
  CelValidationResult validate(CelAbstractSyntaxTree checkedAst) {
    return CEL_VALIDATOR.validate(checkedAst);
  }

  /** Evaluates the compiled AST. */
  Object eval(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    return CEL_RUNTIME.createProgram(ast).eval();
  }

  /**
   * Performs general validation on AttributeContext.Request message. The validator raises errors if
   * the HTTP request is malformed and semantically invalid (e.g: contains disallowed HTTP methods).
   * Warnings are presented if there's potential problems with the contents of the request (e.g:
   * using "http" instead of "https" for scheme).
   */
  static final class AttributeContextRequestValidator {
    // Implement validate method here
  }

  /** Prevents nesting an expensive function call within a macro. */
  static final class ComprehensionSafetyValidator {
    // Implement validate method here
  }
}
