// Copyright 2025 Google LLC
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
 * An exception that's raised when a strict call failed to invoke, which includes the source of
 * expression ID, along with canonical CelErrorCode.
 *
 * <p>Note that StrictErrorException should not be surfaced directly back to the user.
 */
final class StrictErrorException extends CelRuntimeException {

  private final long exprId;

  long exprId() {
    return exprId;
  }

  StrictErrorException(CelRuntimeException cause, long exprId) {
    this(cause, cause.getErrorCode(), exprId);
  }

  StrictErrorException(Throwable cause, CelErrorCode errorCode, long exprId) {
    super(cause, errorCode);
    this.exprId = exprId;
  }
}
