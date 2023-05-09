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

package dev.cel.common;

/** The canonical error codes for CEL */
public enum CelErrorCode {
  /**
   * CEL encountered an unexpected error. This can range from CEL encountering impossible states, a
   * failure with dependency or the exact cause of the error is otherwise unknown.
   */
  INTERNAL_ERROR,
  /** Map or object access using an unknown attribute. */
  ATTRIBUTE_NOT_FOUND,
  /** List index is out of bounds. */
  INDEX_OUT_OF_BOUNDS,
  /** Division by zero, also reported for modulus operations. */
  DIVIDE_BY_ZERO,
  /** Map or object construction supplies same key value more than once. */
  DUPLICATE_ATTRIBUTE,
  /** Invalid argument supplied to a function. */
  INVALID_ARGUMENT,
  /** Function defined, but no matching overload found. */
  OVERLOAD_NOT_FOUND,
  /** Multiple matching overloads found. */
  AMBIGUOUS_OVERLOAD,
  /** Type definition could not be found. */
  TYPE_NOT_FOUND,
  /** Numeric overflow occurred due to arithmetic or conversions outside represented range. */
  NUMERIC_OVERFLOW,
  /**
   * Evaluation halted due to reaching the max number of iterations permitted within comprehension
   * loops.
   */
  ITERATION_BUDGET_EXCEEDED,
  /** Conversion failed due to a mismatch in format specification. */
  BAD_FORMAT
}
