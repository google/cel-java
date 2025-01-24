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
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelException;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.SafeStringFormatter;
import org.jspecify.annotations.Nullable;

/**
 * An exception produced during interpretation of expressions.
 *
 * <p>TODO: Remove in favor of creating exception types that corresponds to the error
 * code.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public class InterpreterException extends CelException {

  /** Builder for InterpreterException. */
  public static class Builder {
    private final String message;
    @Nullable private String location;
    private int position;
    private Throwable cause;
    private CelErrorCode errorCode = CelErrorCode.INTERNAL_ERROR;

    @SuppressWarnings({"AnnotateFormatMethod"}) // Format strings are optional.
    public Builder(String message, Object... args) {
      this.message = SafeStringFormatter.format(message, args);
    }

    @SuppressWarnings({"AnnotateFormatMethod"}) // Format strings are optional.
    public Builder(RuntimeException e, String message, Object... args) {
      if (e instanceof CelRuntimeException) {
        CelRuntimeException celRuntimeException = (CelRuntimeException) e;
        this.errorCode = celRuntimeException.getErrorCode();
        // CelRuntimeException is just a wrapper for the specific RuntimeException (typically
        // IllegalArgumentException). The underlying cause and its message is what we are actually
        // interested in.
        this.cause = e.getCause();
        message = e.getCause().getMessage();
      } else {
        this.cause = e;
      }

      this.message = SafeStringFormatter.format(message, args);
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

    @CanIgnoreReturnValue
    public Builder setErrorCode(CelErrorCode errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @CheckReturnValue
    public InterpreterException build() {
      String exceptionMessage = message;
      if (!exceptionMessage.startsWith("evaluation error")) {
        // TODO: Temporary until interpreter exception is removed
        exceptionMessage =
            String.format(
                "evaluation error%s: %s",
                location != null ? " at " + location + ":" + position : "", message);
      }
      return new InterpreterException(exceptionMessage, cause, errorCode);
    }
  }

  public static InterpreterException wrapOrThrow(Metadata metadata, long exprId, Exception e) {
    if (e instanceof InterpreterException) {
      return (InterpreterException) e;
    }
    if (e instanceof CelException) {
      return new InterpreterException.Builder(e.getMessage())
          .setCause(e.getCause())
          .setErrorCode(((CelException) e).getErrorCode())
          .build();
    }
    return new InterpreterException.Builder(e.getMessage()).setCause(e).build();
  }

  public static InterpreterException wrapOrThrow(Exception e) {
    return wrapOrThrow(null, 0, e);
  }

  private InterpreterException(String message, Throwable cause, CelErrorCode errorCode) {
    super(message, cause, errorCode);
  }
}
