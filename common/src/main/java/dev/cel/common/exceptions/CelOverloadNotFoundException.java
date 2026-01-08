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

package dev.cel.common.exceptions;

import dev.cel.common.CelErrorCode;
import dev.cel.common.annotations.Internal;
import java.util.Collection;
import java.util.Collections;

/** Indicates that a matching overload could not be found during function dispatch. */
@Internal
public final class CelOverloadNotFoundException extends CelRuntimeException {

  public CelOverloadNotFoundException(String functionName) {
    this(functionName, Collections.emptyList());
  }

  public CelOverloadNotFoundException(String functionName, Collection<String> overloadIds) {
    super(formatErrorMessage(functionName, overloadIds), CelErrorCode.OVERLOAD_NOT_FOUND);
  }

  private static String formatErrorMessage(String functionName, Collection<String> overloadIds) {
    StringBuilder sb = new StringBuilder();
    sb.append("No matching overload for function '").append(functionName).append("'.");
    if (!overloadIds.isEmpty()) {
      sb.append(" Overload candidates: ").append(String.join(", ", overloadIds));
    }

    return sb.toString();
  }
}
