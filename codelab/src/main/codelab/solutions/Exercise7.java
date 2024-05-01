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

import com.google.rpc.context.AttributeContext.Request;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;

/**
 * Exercise7 introduces macros for dealing with repeated fields and maps.
 *
 * <p>Determine whether the `jwt.extra_claims` has at least one key that starts with the `group`
 * prefix, and ensure that all group-like keys have list values containing only strings that end
 * with '@acme.co`.
 */
final class Exercise7 {

  /**
   * Compiles the input expression.
   *
   * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
   *     errors.
   */
  CelAbstractSyntaxTree compile(String expression) {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("jwt", SimpleType.DYN)
            .setStandardMacros(
                CelStandardMacro.ALL, CelStandardMacro.FILTER, CelStandardMacro.EXISTS)
            .setResultType(SimpleType.BOOL)
            .build();

    try {
      return celCompiler.compile(expression).getAst();
    } catch (CelValidationException e) {
      throw new IllegalArgumentException("Failed to compile expression.", e);
    }
  }

  /** Evaluates the compiled AST with the user provided parameter values. */
  Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues) {
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(Request.getDescriptor())
            .build();

    try {
      CelRuntime.Program program = celRuntime.createProgram(ast);
      return program.eval(parameterValues);
    } catch (CelEvaluationException e) {
      throw new IllegalArgumentException("Evaluation error has occurred.", e);
    }
  }
}
