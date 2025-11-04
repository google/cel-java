// Copyright 2023 Google LLC
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

package dev.cel.validator.validators;

import java.time.Instant;

/** TimestampLiteralValidator ensures that timestamp literal arguments are valid. */
public final class TimestampLiteralValidator extends LiteralValidator {
  public static final TimestampLiteralValidator INSTANCE =
      new TimestampLiteralValidator("timestamp", Instant.class);

  private TimestampLiteralValidator(String functionName, Class<?> expectedResultType) {
    super(functionName, expectedResultType);
  }
}
