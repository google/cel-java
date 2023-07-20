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

import dev.cel.expr.ParsedExpr;

/**
 * Provides an unparsing utility that converts a ParsedExpr back into a human readable format.
 *
 * <p>Input to the unparser is a ParsedExpr. The unparser does not do any checks to see if the
 * ParsedExpr is syntactically or semantically correct but does checks enough to prevent its crash
 * and might return errors in such cases.
 */
public interface CelUnparser {

  /**
   * Unparses the {@link ParsedExpr} value to a human-readable string.
   *
   * <p>For the best results ensure that the expression is parsed with ParserOptions.add_macro_calls
   * = true.
   */
  String unparse(ParsedExpr parsedExpr);
}
