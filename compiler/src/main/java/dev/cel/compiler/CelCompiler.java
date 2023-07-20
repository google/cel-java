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

package dev.cel.compiler;

import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelChecker;
import dev.cel.common.CelValidationResult;
import dev.cel.parser.CelParser;

/**
 * CelCompiler bundles up the common concerns for parsing and type-checking exposes additional
 * methods for performing both operations in a single pass.
 */
@Immutable
public interface CelCompiler extends CelParser, CelChecker {

  /**
   * Compile the input {@code expression} and return a {@code CelValidationResult}.
   *
   * <p>Compile will {@code parse}, then {@code check} the {@code expression} to validate the syntax
   * and type-agreement of the expression.
   */
  default CelValidationResult compile(String expression) {
    return compile(expression, "<input>");
  }

  /**
   * Compile the input {@code expression} and return a {@code CelValidationResult}.
   *
   * <p>The {@code description} may be used to help tailor error messages for the location where the
   * {@code expression} originates, e.g. a file name or form UI element.
   *
   * <p>Compile will {@code parse}, then {@code check} the {@code expression} to validate the syntax
   * and type-agreement of the expression.
   */
  default CelValidationResult compile(String expression, String description) {
    CelValidationResult result = parse(expression, description);
    if (result.hasError()) {
      return result;
    }

    try {
      return check(result.getAst());
    } catch (Exception ex) {
      throw new IllegalStateException("this method must only be called when !hasError()", ex);
    }
  }
}
