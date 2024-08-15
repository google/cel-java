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

package dev.cel.validator.validators;

import static com.google.common.base.Preconditions.checkArgument;

import dev.cel.bundle.Cel;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.validator.CelAstValidator;

/** Enforces a compiled AST to stay below the configured depth limit. */
public final class AstDepthLimitValidator implements CelAstValidator {

  // Protobuf imposes a default parse-depth limit of 100. We set it to half here because navigable
  // expr does not include operands in the depth calculation.
  // As an example, an expression 'x.y' has a depth of 2 in NavigableExpr, but the ParsedExpr has a
  // depth of 4 as illustrated below:
  //
  // expr {
  //   id: 2
  //   select_expr {
  //     operand {
  //       id: 1
  //       ident_expr {
  //         name: "x"
  //       }
  //     }
  //     field: "y"
  //   }
  // }
  static final int DEFAULT_DEPTH_LIMIT = 50;

  public static final AstDepthLimitValidator DEFAULT = newInstance(DEFAULT_DEPTH_LIMIT);
  private final int maxDepth;

  /**
   * Constructs a new instance of {@link AstDepthLimitValidator} with the configured maxDepth as its
   * limit.
   */
  public static AstDepthLimitValidator newInstance(int maxDepth) {
    checkArgument(maxDepth > 0);
    return new AstDepthLimitValidator(maxDepth);
  }

  @Override
  public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
    if (navigableAst.getRoot().height() >= maxDepth) {
      issuesFactory.addError(
          navigableAst.getRoot().id(),
          String.format("AST's depth exceeds the configured limit: %s.", maxDepth));
    }
  }

  private AstDepthLimitValidator(int maxDepth) {
    this.maxDepth = maxDepth;
  }
}
