// Copyright 2026 Google LLC
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

package dev.cel.runtime.planner;

import dev.cel.common.CelErrorCode;
import dev.cel.common.exceptions.CelRuntimeException;

/**
 * Wraps a {@link CelRuntimeException} with its source expression ID for error reporting.
 *
 * <p>This is the ONLY exception type that propagates through evaluation in the planner. All
 * CelRuntimeExceptions from runtime helpers are immediately wrapped with location information to
 * track where the error occurred in the expression tree.
 *
 * <p>Note: This exception should not be surfaced directly to users - it's unwrapped in {@link
 * PlannedProgram}.
 */
final class LocalizedEvaluationException extends CelRuntimeException {

  private final long exprId;

  long exprId() {
    return exprId;
  }

  LocalizedEvaluationException(CelRuntimeException cause, long exprId) {
    this(cause, cause.getErrorCode(), exprId);
  }

  LocalizedEvaluationException(Throwable cause, CelErrorCode errorCode, long exprId) {
    super(cause, errorCode);
    this.exprId = exprId;
  }
}
