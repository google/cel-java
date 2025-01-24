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

package dev.cel.runtime;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.CelErrorCode;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.SafeStringFormatter;
import org.jspecify.annotations.Nullable;

/** CEL Library Internals. Do not use. */
@Internal
public final class CelEvaluationExceptionBuilder {

  private String message = "";
  private Throwable cause;
  private CelErrorCode errorCode;
  private String errorLocation;

  @CanIgnoreReturnValue
  public CelEvaluationExceptionBuilder setCause(@Nullable Throwable cause) {
    this.cause = cause;
    return this;
  }

  @CanIgnoreReturnValue
  public CelEvaluationExceptionBuilder setErrorCode(CelErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  @CanIgnoreReturnValue
  public CelEvaluationExceptionBuilder setMetadata(Metadata metadata, long exprId) {
    this.errorLocation =
        SafeStringFormatter.format(
            " at %s:%d", metadata.getLocation(), metadata.getPosition(exprId));
    return this;
  }

  /**
   * Constructs a new {@link CelEvaluationException} instance.
   *
   * <p>CEL Library Internals. Do not use.
   */
  @Internal
  public CelEvaluationException build() {
    // TODO: Temporary until InterpreterException removal is complete
    if (!message.startsWith("evaluation error")) {
      message = SafeStringFormatter.format("evaluation error%s: %s", errorLocation, message);
    }

    return new CelEvaluationException(message, cause, errorCode);
  }

  /**
   * Constructs a new builder for {@link CelEvaluationException}
   *
   * <p>CEL Library Internals. Do not use.
   */
  @Internal
  public static CelEvaluationExceptionBuilder newBuilder(String message, Object... args) {
    return new CelEvaluationExceptionBuilder(SafeStringFormatter.format(message, args));
  }

  private CelEvaluationExceptionBuilder(String message) {
    this.message = message;
    this.errorCode = CelErrorCode.INTERNAL_ERROR;
    this.errorLocation = "";
  }
}
