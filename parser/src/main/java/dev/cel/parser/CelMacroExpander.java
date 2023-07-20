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

package dev.cel.parser;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.ast.CelExpr;
import java.util.Optional;

/** Converts the target and arguments of a function call that matches a macro. */
@FunctionalInterface
@Immutable
public interface CelMacroExpander {

  /**
   * Converts a call and its associated arguments into a new CEL AST.
   *
   * @param exprFactory Expression factory to assist with expansion of this macro.
   * @param target Target expression which the macro is being invoked on. Default instance of Expr
   *     is provided instead for global macros.
   * @param arguments Arguments of the call
   * @return A newly generated CEL AST. Implementations may return Optional.Empty instead to signal
   *     that an expansion is not needed.
   */
  Optional<CelExpr> expandMacro(
      CelExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments);
}
