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

package dev.cel.runtime;

import dev.cel.expr.CheckedExpr;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;

/**
 * Interface to a CEL interpreter.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@ThreadSafe
@Internal
public interface Interpreter {

  /**
   * Creates an interpretable for the given expression.
   *
   * <p>This method may run pre-processing and partial evaluation of the expression it gets passed.
   *
   * @deprecated Use {@link #createInterpretable(CelAbstractSyntaxTree)} instead.
   */
  @Deprecated
  Interpretable createInterpretable(CheckedExpr checkedExpr) throws InterpreterException;

  /**
   * Creates an interpretable for the given expression.
   *
   * <p>This method may run pre-processing and partial evaluation of the expression it gets passed.
   */
  Interpretable createInterpretable(CelAbstractSyntaxTree ast);
}
