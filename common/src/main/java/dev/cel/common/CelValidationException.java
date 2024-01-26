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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;

/** Base class for all checked exceptions explicitly thrown by the library during parsing. */
public final class CelValidationException extends CelException {

  private static final Joiner JOINER = Joiner.on('\n');
  // Truncates all errors beyond this limit in the message.
  private static final int MAX_ERRORS_TO_REPORT = 1000;

  private final CelSource source;
  private final ImmutableList<CelIssue> errors;

  @VisibleForTesting
  public CelValidationException(CelSource source, List<CelIssue> errors) {
    super(safeJoinErrorMessage(source, errors));
    this.source = source;
    this.errors = ImmutableList.copyOf(errors);
  }

  CelValidationException(
      CelSource source, Iterable<CelIssue> errors, String message, Throwable cause) {
    super(message, cause);
    this.source = source;
    this.errors = ImmutableList.copyOf(errors);
  }

  private static String safeJoinErrorMessage(CelSource source, List<CelIssue> errors) {
    if (errors.size() <= MAX_ERRORS_TO_REPORT) {
      return JOINER.join(Iterables.transform(errors, error -> error.toDisplayString(source)));
    }

    List<CelIssue> truncatedErrors = errors.subList(0, MAX_ERRORS_TO_REPORT);
    StringBuilder sb = new StringBuilder();
    JOINER.appendTo(
        sb, Iterables.transform(truncatedErrors, error -> error.toDisplayString(source)));
    sb.append(
        String.format("%n...and %d more errors (truncated)", errors.size() - MAX_ERRORS_TO_REPORT));

    return sb.toString();
  }

  /** Returns the {@link CelSource} that was being validated. */
  public CelSource getSource() {
    return source;
  }

  /** Returns a list of {@link CelIssue} with error severity that occurred during validation. */
  public ImmutableList<CelIssue> getErrors() {
    return errors;
  }
}
