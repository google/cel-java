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

package dev.cel.runtime.standard;

import com.google.protobuf.Timestamp;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

final class DateTimeHelpers {
  static final String UTC = "UTC";

  /**
   * Constructs a new {@link LocalDateTime} instance
   *
   * @param ts Timestamp protobuf object
   * @param tz Timezone based on the CEL specification. This is either the canonical name from tz
   *     database or a standard offset represented in (+/-)HH:MM. Few valid examples are:
   *     <ul>
   *       <li>UTC
   *       <li>America/Los_Angeles
   *       <li>-09:30 or -9:30 (Leading zeroes can be omitted though not allowed by spec)
   *     </ul>
   *
   * @return If an Invalid timezone is supplied.
   */
  static LocalDateTime newLocalDateTime(Timestamp ts, String tz) {
    return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())
        .atZone(timeZone(tz))
        .toLocalDateTime();
  }

  /**
   * Get the DateTimeZone Instance.
   *
   * @param tz the ID of the datetime zone
   * @return the ZoneId object
   */
  private static ZoneId timeZone(String tz) {
    try {
      return ZoneId.of(tz);
    } catch (DateTimeException e) {
      // If timezone is not a string name (for example, 'US/Central'), it should be a numerical
      // offset from UTC in the format [+/-]HH:MM.
      try {
        int ind = tz.indexOf(":");
        if (ind == -1) {
          throw new CelRuntimeException(e, CelErrorCode.BAD_FORMAT);
        }

        int hourOffset = Integer.parseInt(tz.substring(0, ind));
        int minOffset = Integer.parseInt(tz.substring(ind + 1));
        // Ensures that the offset are properly formatted in [+/-]HH:MM to conform with
        // ZoneOffset's format requirements.
        // Example: "-9:30" -> "-09:30" and "9:30" -> "+09:30"
        String formattedOffset =
            ((hourOffset < 0) ? "-" : "+")
                + String.format(Locale.getDefault(), "%02d:%02d", Math.abs(hourOffset), minOffset);

        return ZoneId.of(formattedOffset);

      } catch (DateTimeException e2) {
        throw new CelRuntimeException(e2, CelErrorCode.BAD_FORMAT);
      }
    }
  }

  private DateTimeHelpers() {}
}
