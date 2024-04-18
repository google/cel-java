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

package dev.cel.common.navigation;

import dev.cel.common.ast.CelMutableAst;
import dev.cel.common.types.CelType;
import java.util.Optional;

/**
 * Decorates a {@link CelMutableAst} with navigational properties. This allows us to visit a node's
 * children, descendants or its parent with ease.
 */
public final class CelNavigableMutableAst {

  private final CelMutableAst ast;
  private final CelNavigableMutableExpr root;

  private CelNavigableMutableAst(CelMutableAst mutableAst) {
    this.ast = mutableAst;
    this.root = CelNavigableMutableExpr.fromExpr(mutableAst.expr());
  }

  /** Constructs a new instance of {@link CelNavigableMutableAst} from {@link CelMutableAst}. */
  public static CelNavigableMutableAst fromAst(CelMutableAst ast) {
    return new CelNavigableMutableAst(ast);
  }

  /** Returns the root of the AST. */
  public CelNavigableMutableExpr getRoot() {
    return root;
  }

  /** Returns the underlying {@link CelMutableAst}. */
  public CelMutableAst getAst() {
    return ast;
  }

  /**
   * Returns the type of the expression node for a type-checked AST. This simply proxies down the
   * call to {@link CelMutableAst#getType(long)}.
   *
   * @return Optional of {@link CelType} or {@link Optional#empty} if the type does not exist at the
   *     ID.
   */
  public Optional<CelType> getType(long exprId) {
    return ast.getType(exprId);
  }
}
