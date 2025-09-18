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

package dev.cel.common.internal;

import com.google.common.base.Strings;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

/** Collection of utility methods for CEL datetime handlings. */
@Internal
@SuppressWarnings("JavaInstantGetSecondsGetNano") // Intended within CEL.
public final class DateTimeHelpers {
  public static final String UTC = "UTC";

  // Timestamp for "0001-01-01T00:00:00Z"
  private static final long TIMESTAMP_SECONDS_MIN = -62135596800L;
  // Timestamp for "9999-12-31T23:59:59Z"
  private static final long TIMESTAMP_SECONDS_MAX = 253402300799L;

  private static final long DURATION_SECONDS_MIN = -315576000000L;
  private static final long DURATION_SECONDS_MAX = 315576000000L;
  private static final int NANOS_PER_SECOND = 1000000000;

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
  public static LocalDateTime newLocalDateTime(Timestamp ts, String tz) {
    return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())
        .atZone(timeZone(tz))
        .toLocalDateTime();
  }

  /**
   * Constructs a new {@link LocalDateTime} instance from a Java Instant.
   *
   * @param instant Instant object
   * @param tz Timezone based on the CEL specification. This is either the canonical name from tz
   *     database or a standard offset represented in (+/-)HH:MM. Few valid examples are:
   *     <ul>
   *       <li>UTC
   *       <li>America/Los_Angeles
   *       <li>-09:30 or -9:30 (Leading zeroes can be omitted though not allowed by spec)
   *     </ul>
   *
   * @return A new {@link LocalDateTime} instance.
   */
  public static LocalDateTime newLocalDateTime(Instant instant, String tz) {
    return instant.atZone(timeZone(tz)).toLocalDateTime();
  }

  /**
   * Parse from RFC 3339 date string to {@link java.time.Instant}.
   *
   * <p>Example of accepted format: "1972-01-01T10:00:20.021-05:00"
   */
  public static Instant parse(String text) {
    OffsetDateTime offsetDateTime = OffsetDateTime.parse(text);
    Instant instant = offsetDateTime.toInstant();
    checkValid(instant);

    return instant;
  }

  /** Adds a duration to an instant. */
  public static Instant add(Instant ts, Duration dur) {
    Instant newInstant = ts.plus(dur);
    checkValid(newInstant);

    return newInstant;
  }

  /** Adds two durations */
  public static Duration add(Duration d1, Duration d2) {
    Duration newDuration = d1.plus(d2);
    checkValid(newDuration);

    return newDuration;
  }

  /** Subtracts a duration to an instant. */
  public static Instant subtract(Instant ts, Duration dur) {
    Instant newInstant = ts.minus(dur);
    checkValid(newInstant);

    return newInstant;
  }

  /** Subtract a duration from another. */
  public static Duration subtract(Duration d1, Duration d2) {
    Duration newDuration = d1.minus(d2);
    checkValid(newDuration);

    return newDuration;
  }

  /**
   * Formats a {@link Duration} into a minimal seconds-based representation.
   *
   * <p>Note: follows {@code ProtoTimeUtils#toString(Duration)} implementation
   */
  public static String toString(Duration duration) {
    if (duration.isZero()) {
      return "0s";
    }

    long totalNanos = duration.toNanos();
    StringBuilder sb = new StringBuilder();

    if (totalNanos < 0) {
      sb.append('-');
      totalNanos = -totalNanos;
    }

    long seconds = totalNanos / 1_000_000_000;
    int nanos = (int) (totalNanos % 1_000_000_000);

    sb.append(seconds);

    // Follows ProtoTimeUtils.toString(Duration) implementation
    if (nanos > 0) {
      sb.append('.');
      if (nanos % 1_000_000 == 0) {
        // Millisecond precision (3 digits)
        int millis = nanos / 1_000_000;
        sb.append(String.format(Locale.US, "%03d", millis));
      } else if (nanos % 1_000 == 0) {
        // Microsecond precision (6 digits)
        int micros = nanos / 1_000;
        sb.append(String.format(Locale.US, "%06d", micros));
      } else {
        // Nanosecond precision (9 digits)
        sb.append(String.format(Locale.US, "%09d", nanos));
      }
    }

    sb.append('s');
    return sb.toString();
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
                + String.format(Locale.US, "%02d:%02d", Math.abs(hourOffset), minOffset);

        return ZoneId.of(formattedOffset);

      } catch (DateTimeException e2) {
        throw new CelRuntimeException(e2, CelErrorCode.BAD_FORMAT);
      }
    }
  }

  /** Throws an {@link IllegalArgumentException} if the given {@link Timestamp} is not valid. */
  private static void checkValid(Instant instant) {
    long seconds = instant.getEpochSecond();

    if (seconds < TIMESTAMP_SECONDS_MIN || seconds > TIMESTAMP_SECONDS_MAX) {
      throw new IllegalArgumentException(
          Strings.lenientFormat(
              "Timestamp is not valid. "
                  + "Seconds (%s) must be in range [-62,135,596,800, +253,402,300,799]. "
                  + "Nanos (%s) must be in range [0, +999,999,999].",
              seconds, instant.getNano()));
    }
  }

  /** Throws an {@link IllegalArgumentException} if the given {@link Duration} is not valid. */
  private static void checkValid(Duration duration) {
    long seconds = duration.getSeconds();
    int nanos = duration.getNano();
    if (!isDurationValid(seconds, nanos)) {
      throw new IllegalArgumentException(
          Strings.lenientFormat(
              "Duration is not valid. "
                  + "Seconds (%s) must be in range [-315,576,000,000, +315,576,000,000]. "
                  + "Nanos (%s) must be in range [-999,999,999, +999,999,999]. "
                  + "Nanos must have the same sign as seconds",
              seconds, nanos));
    }
  }

  /**
   * Returns true if the given number of seconds and nanos is a valid {@link Duration}. The {@code
   * seconds} value must be in the range [-315,576,000,000, +315,576,000,000]. The {@code nanos}
   * value must be in the range [-999,999,999, +999,999,999].
   *
   * <p><b>Note:</b> Durations less than one second are represented with a 0 {@code seconds} field
   * and a positive or negative {@code nanos} field. For durations of one second or more, a non-zero
   * value for the {@code nanos} field must be of the same sign as the {@code seconds} field.
   */
  private static boolean isDurationValid(long seconds, int nanos) {
    if (seconds < DURATION_SECONDS_MIN || seconds > DURATION_SECONDS_MAX) {
      return false;
    }
    if (nanos < -999999999L || nanos >= NANOS_PER_SECOND) {
      return false;
    }
    if (seconds < 0 || nanos < 0) {
      if (seconds > 0 || nanos > 0) {
        return false;
      }
    }
    return true;
  }

  private DateTimeHelpers() {}
}
