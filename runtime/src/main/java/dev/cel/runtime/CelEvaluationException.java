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

package dev.cel.runtime;

import dev.cel.common.CelErrorCode;
import dev.cel.common.CelException;

/**
 * CelEvaluationException encapsulates the potential issues which could arise during the
 * configuration or evaluation of an expression {@code Program}.
 */
public final class CelEvaluationException extends CelException {

  public CelEvaluationException(String message) {
    super(formatErrorMessage(message));
  }

  public CelEvaluationException(String message, Throwable cause) {
    super(formatErrorMessage(message), cause);
  }

  public CelEvaluationException(String message, CelErrorCode errorCode) {
    super(formatErrorMessage(message), errorCode);
  }

  public CelEvaluationException(String message, Throwable cause, CelErrorCode errorCode) {
    this(message, cause, errorCode, true);
  }

  CelEvaluationException(
      String message, Throwable cause, CelErrorCode errorCode, boolean formatErrorMessage) {
    super(formatErrorMessage ? formatErrorMessage(message) : message, cause, errorCode);
  }

  private static String formatErrorMessage(String message) {
    return String.format("evaluation error: %s", message);
  }
}
