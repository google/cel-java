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
import dev.cel.common.annotations.Internal;
import java.util.Arrays;
import java.util.Collection;

/** Indicates an attempt to access a map or object using an invalid attribute or key. */
@Internal
public final class CelAttributeNotFoundException extends CelRuntimeException {

  public static CelAttributeNotFoundException of(String message) {
    return new CelAttributeNotFoundException(message);
  }

  public static CelAttributeNotFoundException forMissingMapKey(String key) {
    return new CelAttributeNotFoundException(String.format("key '%s' is not present in map.", key));
  }

  public static CelAttributeNotFoundException forFieldResolution(String... fields) {
    return forFieldResolution(Arrays.asList(fields));
  }

  public static CelAttributeNotFoundException forFieldResolution(Collection<String> fields) {
    return new CelAttributeNotFoundException(formatErrorMessage(fields));
  }

  private static String formatErrorMessage(Collection<String> fields) {
    String maybePlural = "";
    if (fields.size() > 1) {
      maybePlural = "s";
    }

    return String.format(
        "Error resolving field%s '%s'. Field selections must be performed on messages or maps.",
        maybePlural, String.join(", ", fields));
  }

  private CelAttributeNotFoundException(String message) {
    super(message, CelErrorCode.ATTRIBUTE_NOT_FOUND);
  }
}
