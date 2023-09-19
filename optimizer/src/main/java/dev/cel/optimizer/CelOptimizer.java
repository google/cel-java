// Copyright 2023 Google LLC
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

package dev.cel.optimizer;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;

/** Public interface for optimizing an AST. */
public interface CelOptimizer {

  /**
   * Performs custom optimization of the provided AST.
   *
   * <p>This invokes all the AST optimizers present in this CelOptimizer instance via {@link
   * CelOptimizerBuilder#addAstOptimizers} in their added order. Any exceptions thrown within the
   * AST optimizer will be propagated to the caller and will abort the optimization process.
   *
   * <p>Note that the produced expression string from unparsing an optimized AST will likely not be
   * equal to the original expression.
   *
   * @param ast A type-checked AST.
   * @throws CelValidationException If the optimized AST fails to type-check after a single
   *     optimization pass.
   */
  CelAbstractSyntaxTree optimize(CelAbstractSyntaxTree ast) throws CelValidationException;
}
