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

import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.navigation.CelNavigableAst;

/** Public interface for performing a single, custom optimization on an AST. */
public interface CelAstOptimizer {

  /** Optimizes a single AST. */
  CelAbstractSyntaxTree optimize(CelNavigableAst navigableAst, Cel cel)
      throws CelOptimizationException;

  /**
   * Replaces a subtree in the given AST. This operation is intended for AST optimization purposes.
   *
   * <p>This is a very dangerous operation. Callers should re-typecheck the mutated AST and
   * additionally verify that the resulting AST is semantically valid.
   *
   * <p>All expression IDs will be renumbered in a stable manner to ensure there's no ID collision
   * between the nodes. The renumbering occurs even if the subtree was not replaced.
   *
   * <p>This will scrub out the description, positions and line offsets from {@code CelSource}. If
   * the source contains macro calls, its call IDs will be to be consistent with the renumbered IDs
   * in the AST.
   *
   * @param ast Original ast to mutate.
   * @param newExpr New CelExpr to replace the subtree with.
   * @param exprIdToReplace Expression id of the subtree that is getting replaced.
   */
  default CelAbstractSyntaxTree replaceSubtree(
      CelAbstractSyntaxTree ast, CelExpr newExpr, long exprIdToReplace) {
    return MutableAst.replaceSubtree(ast, newExpr, exprIdToReplace);
  }
}
