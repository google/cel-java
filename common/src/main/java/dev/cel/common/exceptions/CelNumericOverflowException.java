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

package dev.cel.common.exceptions;

import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;

/**
 * Indicates that a numeric overflow occurred due to arithmetic operations or conversions resulting
 * in a value outside the representable range.
 */
@Internal
public final class CelNumericOverflowException extends CelRuntimeException {

  public CelNumericOverflowException(String message) {
    super(message, CelErrorCode.NUMERIC_OVERFLOW);
  }

  public CelNumericOverflowException(Throwable cause) {
    super(cause, CelErrorCode.NUMERIC_OVERFLOW);
  }
}
