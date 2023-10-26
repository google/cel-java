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
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;

final class Exercise3 {

  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder().setResultType(SimpleType.BOOL).build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder().build();

  /**
   * Compiles the given expression and evaluates it.
   *
   * @throws IllegalArgumentException If the expression fails to compile or evaluate
   */
  Object compileAndEvaluate(String expression) {
    CelAbstractSyntaxTree ast;
    try {
      ast = CEL_COMPILER.compile(expression).getAst();
    } catch (CelValidationException e) {
      throw new IllegalArgumentException("Failed to compile expression.", e);
    }

    try {
      CelRuntime.Program program = CEL_RUNTIME.createProgram(ast);
      return program.eval();
    } catch (CelEvaluationException e) {
      throw new IllegalArgumentException("Evaluation error has occurred.", e);
    }
  }
}
