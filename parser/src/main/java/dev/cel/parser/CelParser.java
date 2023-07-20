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

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationResult;

/** Public interface for the parsing CEL expressions. */
@Immutable
public interface CelParser {

  /**
   * Parse the input {@code expression} and return a {@code CelValidationResult}.
   *
   * <p>Parse validates the syntax of an expression.
   */
  default CelValidationResult parse(String expression) {
    return parse(expression, "<input>");
  }

  /**
   * Parse the input {@code expression} and return a {@code CelValidationResult}.
   *
   * <p>The {@code description} may be used to help tailor error messages for the location where the
   * {@code expression} originates, e.g. a file name or form UI element.
   *
   * <p>Parse validates the syntax of an expression.
   */
  CelValidationResult parse(String expression, String description);

  /**
   * Parse the input {@code expression} and return a {@code CelValidationResult}.
   *
   * <p>The {@code description} may be used to help tailor error messages for the location where the
   * {@code expression} originates, e.g. a file name or form UI element.
   *
   * <p>Parse validates the syntax of an expression.
   */
  @CheckReturnValue
  CelValidationResult parse(CelSource source);
}
