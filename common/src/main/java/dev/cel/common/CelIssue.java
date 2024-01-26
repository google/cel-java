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

package dev.cel.common;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.internal.SafeStringFormatter;
import java.util.Optional;
import java.util.PrimitiveIterator;

/**
 * Encapulates a {@link CelSourceLocation} and message representing an error within an expression.
 */
@AutoValue
@Immutable
@SuppressWarnings("UnicodeEscape") // Suppressed to distinguish half-width and full-width chars.
public abstract class CelIssue {

  /** Severity of a CelIssue. */
  public enum Severity {
    ERROR,
    WARNING,
    INFORMATION,
    DEPRECATED;
  }

  // Package-private default constructor to prevent unexpected extensions outside of this codebase.
  CelIssue() {}

  public abstract Severity getSeverity();

  public abstract CelSourceLocation getSourceLocation();

  public abstract String getMessage();

  public static Builder newBuilder() {
    return new AutoValue_CelIssue.Builder();
  }

  /**
   * Build {@link CelIssue} from the given {@link CelSourceLocation}, format string, and arguments.
   */
  public static CelIssue formatError(CelSourceLocation sourceLocation, String message) {
    return newBuilder()
        .setSeverity(Severity.ERROR)
        .setSourceLocation(sourceLocation)
        .setMessage(message)
        .build();
  }

  /** Build {@link CelIssue} from the given line, column, format string, and arguments. */
  public static CelIssue formatError(int line, int column, String message) {
    return formatError(CelSourceLocation.of(line, column), message);
  }

  // Halfwidth '.' and '^'.
  private static final char DOT = '\u002e';
  private static final char HAT = '\u005e';
  // Fullwidth '.' and '^'.
  private static final char WIDE_DOT = '\uff0e';
  private static final char WIDE_HAT = '\uff3e';

  /** Returns a string representing this error that is suitable for displaying to humans. */
  public String toDisplayString(CelSource source) {
    // Based onhttps://github.com/google/cel-go/blob/v0.5.1/common/error.go#L42.
    String result =
        SafeStringFormatter.format(
            "%s: %s:%d:%d: %s",
            getSeverity(),
            source.getDescription(),
            getSourceLocation().getLine(),
            getSourceLocation().getColumn() + 1,
            getMessage());
    if (!CelSourceLocation.NONE.equals(getSourceLocation())) {
      Optional<String> optionalSnippet = source.getSnippet(getSourceLocation().getLine());
      if (optionalSnippet.isPresent()) {
        StringBuilder builder = new StringBuilder();
        String snippet = optionalSnippet.get().replace('\t', ' ');
        builder.append(result).append("\n | ").append(snippet).append("\n | ");
        PrimitiveIterator.OfInt codePoints = snippet.codePoints().iterator();
        for (int index = 0;
            index < getSourceLocation().getColumn() && codePoints.hasNext();
            index++) {
          int codePoint = codePoints.nextInt();
          if (codePoint > 0x7f) {
            // We make a naive assumption that all characters above 0xff are fullwidth.
            // Unfortunately,
            // as of Java 8, there is nothing in the Character class to determine width.
            builder.append(WIDE_DOT);
          } else {
            builder.append(DOT);
          }
        }
        if (codePoints.hasNext() && codePoints.nextInt() > 0x7f) {
          // See above.
          builder.append(WIDE_HAT);
        } else {
          builder.append(HAT);
        }
        result = builder.toString();
      }
    }
    return result;
  }

  /** Builder for configuring {@link CelIssue}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSeverity(Severity severity);

    public abstract Builder setSourceLocation(CelSourceLocation location);

    public abstract Builder setMessage(String message);

    @CheckReturnValue
    public abstract CelIssue build();
  }
}
