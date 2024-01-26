// Copyright 2024 Google LLC
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

package dev.cel.common.internal;

import com.google.re2j.Pattern;
import dev.cel.common.annotations.Internal;

/**
 * {@link SafeStringFormatter} is a wrapper around JDK's {@link String#format}. It prevents any
 * unsafe string.format calls by only allowing known formatting specifiers to be provided.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class SafeStringFormatter {
  // Allow format specifiers of %d, %f, %s and %n only.
  private static final Pattern FORBIDDEN_FORMAT_SPECIFIERS = Pattern.compile("%[^dfsn]");

  /**
   * Performs a safe {@link String#format}.
   *
   * @param format A format string. Only %d, %f, %s and %n are allowed as formatting specifiers. All
   *     other formatting specifiers will be stripped out.
   * @return A formatted string
   */
  public static String format(String format, Object... args) {
    if (args.length == 0) {
      return format;
    }

    String sanitizedMessage = FORBIDDEN_FORMAT_SPECIFIERS.matcher(format).replaceAll("");
    return String.format(sanitizedMessage, args);
  }

  private SafeStringFormatter() {}
}
