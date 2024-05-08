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

import com.google.rpc.context.AttributeContext;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.optimizer.optimizers.ConstantFoldingOptimizer;
import dev.cel.optimizer.optimizers.SubexpressionOptimizer;
import dev.cel.optimizer.optimizers.SubexpressionOptimizer.SubexpressionOptimizerOptions;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;
import dev.cel.validator.validators.DurationLiteralValidator;
import dev.cel.validator.validators.HomogeneousLiteralValidator;
import dev.cel.validator.validators.RegexLiteralValidator;
import dev.cel.validator.validators.TimestampLiteralValidator;
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

  // Just like the compiler and runtime, the validator and optimizer can be statically
  // initialized as their instances are immutable.
  private static final CelValidator CEL_VALIDATOR =
      CelValidatorFactory.standardCelValidatorBuilder(CEL_COMPILER, CEL_RUNTIME)
          .addAstValidators(
              TimestampLiteralValidator.INSTANCE,
              DurationLiteralValidator.INSTANCE,
              RegexLiteralValidator.INSTANCE,
              HomogeneousLiteralValidator.newInstance())
          .build();
  private static final CelOptimizer CEL_OPTIMIZER =
      CelOptimizerFactory.standardCelOptimizerBuilder(CEL_COMPILER, CEL_RUNTIME)
          .addAstOptimizers(
              ConstantFoldingOptimizer.getInstance(),
              SubexpressionOptimizer.newInstance(
                  SubexpressionOptimizerOptions.newBuilder().enableCelBlock(true).build()))
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

  /**
   * Optimizes a type-checked AST.
   *
   * @throws CelOptimizationException If the optimization fails.
   */
  CelAbstractSyntaxTree optimize(CelAbstractSyntaxTree checkedAst) throws CelOptimizationException {
    return CEL_OPTIMIZER.optimize(checkedAst);
  }

  /** Evaluates the compiled AST with the user provided parameter values. */
  Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues)
      throws CelEvaluationException {
    CelRuntime.Program program = CEL_RUNTIME.createProgram(ast);
    return program.eval(parameterValues);
  }
}
