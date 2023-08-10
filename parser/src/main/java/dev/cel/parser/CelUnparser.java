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

package dev.cel.parser;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;

/**
 * Provides an unparsing utility that converts an AST back into a human-readable format.
 *
 * <p>Input to the unparser is a {@link CelAbstractSyntaxTree}. The unparser does not do any checks
 * to see if the AST is syntactically or semantically correct but does check enough to prevent its
 * crash and might return errors in such cases.
 */
public interface CelUnparser {

  /**
   * Unparses the {@link CelAbstractSyntaxTree} value to a human-readable string.
   *
   * <p>To reconstruct an expression that originally contained a macro call, ensure the expression
   * was parsed with {@link CelOptions#populateMacroCalls()} enabled.
   */
  String unparse(CelAbstractSyntaxTree ast);
}
