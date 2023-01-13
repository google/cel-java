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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.annotations.Internal;
import javax.annotation.Nullable;

/**
 * An exception produced during interpretation of expressions.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public class InterpreterException extends Exception {

  /** Builder for InterpreterException. */
  public static class Builder {
    private final String message;
    @Nullable private String location;
    private int position;
    private Throwable cause;

    @SuppressWarnings({"AnnotateFormatMethod"}) // Format strings are optional.
    public Builder(String message, Object... args) {
      this.message = args.length > 0 ? String.format(message, args) : message;
    }

    @CanIgnoreReturnValue
    public Builder setLocation(@Nullable Metadata metadata, long exprId) {
      if (metadata != null) {
        this.location = metadata.getLocation();
        this.position = metadata.getPosition(exprId);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCause(Throwable cause) {
      this.cause = cause;
      return this;
    }

    @CheckReturnValue
    public InterpreterException build() {
      return new InterpreterException(
          String.format(
              "evaluation error%s: %s",
              location != null ? " at " + location + ":" + position : "", message),
          cause);
    }
  }

  private InterpreterException(String message, Throwable cause) {
    super(message, cause);
  }
}
