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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelVarDecl;

/** Public interface for performing a single, custom optimization on an AST. */
public interface CelAstOptimizer {

  /** Optimizes a single AST. */
  OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) throws CelOptimizationException;

  /**
   * Denotes the result of a single optimization pass on an AST.
   *
   * <p>The optimizer may optionally populate new variable and function declarations generated as
   * part of optimizing an AST.
   */
  @AutoValue
  abstract class OptimizationResult {
    public abstract CelAbstractSyntaxTree optimizedAst();

    public abstract ImmutableList<CelVarDecl> newVarDecls();

    public abstract ImmutableList<CelFunctionDecl> newFunctionDecls();

    /**
     * Create an optimization result with new declarations. The optimizer must populate these
     * declarations after an optimization pass if they are required for type-checking to success.
     */
    public static OptimizationResult create(
        CelAbstractSyntaxTree optimizedAst,
        ImmutableList<CelVarDecl> newVarDecls,
        ImmutableList<CelFunctionDecl> newFunctionDecls) {
      return new AutoValue_CelAstOptimizer_OptimizationResult(
          optimizedAst, newVarDecls, newFunctionDecls);
    }

    public static OptimizationResult create(CelAbstractSyntaxTree optimizedAst) {
      return create(optimizedAst, ImmutableList.of(), ImmutableList.of());
    }
  }
}
