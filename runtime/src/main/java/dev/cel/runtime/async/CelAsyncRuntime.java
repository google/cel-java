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

package dev.cel.runtime.async;

import javax.annotation.concurrent.ThreadSafe;
import com.google.common.util.concurrent.ListenableFuture;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.UnknownContext;

/**
 * CelAsyncRuntime provides configuration for an async evaluation context.
 *
 * <p>This is a factory for two primitives:
 *
 * <ul>
 *   <li>{@link UnknownContext} manages the state necessary for the intial round of iterative
 *       evaluation. The AsyncProgram implemenation may use its .withX methods to generate an
 *       updated context for later rounds.
 *   <li>{@link AsyncProgram} provides an evaluation manager to automate evaluating and resolving
 *       unknown data
 * </ul>
 */
@ThreadSafe
public interface CelAsyncRuntime {

  /**
   * Initialize a new async context for iterative evaluation.
   *
   * <p>This maintains the state related to tracking which parts of the environment are unknown or
   * have been resolved.
   */
  UnknownContext newAsyncContext();

  /** AsyncProgram wraps a CEL Program with a driver to resolve unknowns as they are encountered. */
  interface AsyncProgram {
    ListenableFuture<Object> evaluateToCompletion(UnknownContext ctx);
  }

  /**
   * Creates an {@link AsyncProgram} for the given AST.
   *
   * @param ast the input CEL expression. must be type checked.
   */
  AsyncProgram createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException;
}
