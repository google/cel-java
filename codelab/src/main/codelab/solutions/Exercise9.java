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

import com.google.common.collect.ImmutableSet;
import com.google.rpc.context.AttributeContext;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelStruct;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.validator.CelAstValidator;
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
          .addAstValidators(
              new AttributeContextRequestValidator(), //
              new ComprehensionSafetyValidator())
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
  static final class AttributeContextRequestValidator implements CelAstValidator {
    private static final ImmutableSet<String> ALLOWED_HTTP_METHODS =
        ImmutableSet.of("GET", "POST", "PUT", "DELETE");

    @Override
    public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
      navigableAst
          .getRoot()
          .allNodes()
          .filter(node -> node.getKind().equals(Kind.STRUCT))
          .map(node -> node.expr().struct())
          .filter(
              struct -> struct.messageName().equals("google.rpc.context.AttributeContext.Request"))
          .forEach(
              struct -> {
                for (CelStruct.Entry entry : struct.entries()) {
                  String fieldKey = entry.fieldKey();
                  if (fieldKey.equals("method")) {
                    String entryStringValue = getStringValue(entry.value());
                    if (!ALLOWED_HTTP_METHODS.contains(entryStringValue)) {
                      issuesFactory.addError(
                          entry.value().id(), entryStringValue + " is not an allowed HTTP method.");
                    }
                  } else if (fieldKey.equals("scheme")) {
                    String entryStringValue = getStringValue(entry.value());
                    if (!entryStringValue.equals("https")) {
                      issuesFactory.addWarning(
                          entry.value().id(), "Prefer using https for safety.");
                    }
                  }
                }
              });
    }

    /**
     * Reads the underlying string value from the expression.
     *
     * @throws UnsupportedOperationException if the expression is not a constant string value.
     */
    private static String getStringValue(CelExpr celExpr) {
      return celExpr.constant().stringValue();
    }
  }

  /** Prevents nesting an expensive function call within a macro. */
  static final class ComprehensionSafetyValidator implements CelAstValidator {
    private static final String EXPENSIVE_FUNCTION_NAME = "is_prime_number";

    @Override
    public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
      navigableAst
          .getRoot()
          .allNodes()
          .filter(node -> node.getKind().equals(Kind.COMPREHENSION))
          .forEach(
              comprehensionNode -> {
                boolean isFunctionWithinMacro =
                    comprehensionNode
                        .descendants()
                        .anyMatch(
                            node ->
                                node.expr()
                                    .callOrDefault()
                                    .function()
                                    .equals(EXPENSIVE_FUNCTION_NAME));
                if (isFunctionWithinMacro) {
                  issuesFactory.addError(
                      comprehensionNode.id(),
                      EXPENSIVE_FUNCTION_NAME + " function cannot be used within CEL macros.");
                }
              });
    }
  }
}
