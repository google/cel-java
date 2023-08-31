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

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExprFactory;

/**
 * Assists with the expansion of {@link CelMacro} in a manner which is consistent with the source
 * position and expression ID generation code leveraged by both the parser and type-checker.
 */
public abstract class CelMacroExprFactory extends CelExprFactory {

  // Package-private default constructor to prevent extensions outside of the codebase.
  CelMacroExprFactory() {}

  /**
   * Creates a {@link CelIssue} and reports it, returning a sentinel {@link CelExpr} that indicates
   * an error.
   */
  @FormatMethod
  public final CelExpr reportError(@FormatString String format, Object... args) {
    return reportError(
        CelIssue.formatError(currentSourceLocationForMacro(), String.format(format, args)));
  }

  /**
   * Creates a {@link CelIssue} and reports it, returning a sentinel {@link CelExpr} that indicates
   * an error.
   */
  public final CelExpr reportError(String message) {
    return reportError(CelIssue.formatError(currentSourceLocationForMacro(), message));
  }

  /** Reports a {@link CelIssue} and returns a sentinel {@link CelExpr} that indicates an error. */
  public abstract CelExpr reportError(CelIssue error);

  /** Retrieves the source location for the given {@link CelExpr} ID. */
  public final CelSourceLocation getSourceLocation(CelExpr expr) {
    return getSourceLocation(expr.id());
  }

  /** Retrieves the source location for the given {@link CelExpr} ID. */
  protected abstract CelSourceLocation getSourceLocation(long exprId);

  /** Returns the current (last known) source location. This should only be used for macros. */
  protected abstract CelSourceLocation currentSourceLocationForMacro();
}
