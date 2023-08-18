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

package dev.cel.common.navigation;

import dev.cel.common.CelAbstractSyntaxTree;

/**
 * Decorates a {@link CelAbstractSyntaxTree} with navigational properties. This allows us to visit a
 * node's children, descendants or its parent with ease.
 */
public final class CelNavigableAst {

  private final CelAbstractSyntaxTree ast;
  private final CelNavigableExpr root;

  private CelNavigableAst(CelAbstractSyntaxTree ast) {
    this.ast = ast;
    this.root = CelNavigableExpr.builder().setExpr(ast.getExpr()).setDepth(0).build();
  }

  /** Constructs a new instance of {@link CelNavigableAst} from {@link CelAbstractSyntaxTree}. */
  public static CelNavigableAst fromAst(CelAbstractSyntaxTree ast) {
    return new CelNavigableAst(ast);
  }

  /** Returns the root of the AST. */
  public CelNavigableExpr getRoot() {
    return root;
  }

  /** Returns the underlying {@link CelAbstractSyntaxTree}. */
  public CelAbstractSyntaxTree getAst() {
    return ast;
  }
}
