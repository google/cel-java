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

import static java.time.Duration.ofSeconds;

import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedLong;
import com.google.common.primitives.UnsignedLongs;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adds standard functions to a {@link Registrar}.
 *
 * @deprecated Do not use. This only exists to maintain compatibility with the legacy async
 *     interpreter, which will be removed in the future.
 */
@Internal
@Deprecated
public class StandardFunctions {
  private static final String UTC = "UTC";

  /**
   * Adds CEL standard functions to the given registrar, omitting those that can be inlined by
   * {@code FuturesInterpreter}.
   *
   * @deprecated Do not use. This only exists to maintain compatibility with the legacy async
   *     interpreter, will be removed in the future.
   */
  @Deprecated
  public static void addNonInlined(Registrar registrar, CelOptions celOptions) {
    addNonInlined(
        registrar,
        new RuntimeEquality(DynamicProto.create(DefaultMessageFactory.INSTANCE)),
        celOptions);
  }

  /**
   * Adds CEL standard functions to the given registrar, omitting those that can be inlined by
   * {@code FuturesInterpreter}.
   */
  private static void addNonInlined(
      Registrar registrar, RuntimeEquality runtimeEquality, CelOptions celOptions) {
    addBoolFunctions(registrar);
    addBytesFunctions(registrar);
    addDoubleFunctions(registrar, celOptions);
    addDurationFunctions(registrar);
    addIntFunctions(registrar, celOptions);
    addListFunctions(registrar, runtimeEquality, celOptions);
    addMapFunctions(registrar, runtimeEquality, celOptions);
    addStringFunctions(registrar, celOptions);
    addTimestampFunctions(registrar);
    if (celOptions.enableUnsignedLongs()) {
      addUintFunctions(registrar, celOptions);
    } else {
      addSignedUintFunctions(registrar, celOptions);
    }
    if (celOptions.enableHeterogeneousNumericComparisons()) {
      addCrossTypeNumericFunctions(registrar);
    }
    addOptionalValueFunctions(registrar, runtimeEquality, celOptions);

    // Common operators.
    registrar.add(
        "equals",
        Object.class,
        Object.class,
        (Object x, Object y) -> runtimeEquality.objectEquals(x, y, celOptions));
    registrar.add(
        "not_equals",
        Object.class,
        Object.class,
        (Object x, Object y) -> !runtimeEquality.objectEquals(x, y, celOptions));

    // Conversion to dyn.
    registrar.add("to_dyn", Object.class, (Object arg) -> arg);
  }

  private static void addBoolFunctions(Registrar registrar) {
    // Identity
    registrar.add("bool_to_bool", Boolean.class, (Boolean x) -> x);
    // Conversion function
    registrar.add(
        "string_to_bool",
        String.class,
        (String str) -> {
          // Note: this is a bit less permissive than what cel-go allows (it accepts '1', 't').
          switch (str) {
            case "true":
            case "TRUE":
            case "True":
            case "t":
            case "1":
              return true;
            case "false":
            case "FALSE":
            case "False":
            case "f":
            case "0":
              return false;
            default:
              throw new InterpreterException.Builder(
                      "Type conversion error from 'string' to 'bool': [%s]", str)
                  .setErrorCode(CelErrorCode.BAD_FORMAT)
                  .build();
          }
        });

    // The conditional, logical_or, logical_and, and not_strictly_false functions are special-cased.
    registrar.add("logical_not", Boolean.class, (Boolean x) -> !x);

    // Boolean ordering functions: <, <=, >=, >
    registrar.add("less_bool", Boolean.class, Boolean.class, (Boolean x, Boolean y) -> !x && y);
    registrar.add(
        "less_equals_bool", Boolean.class, Boolean.class, (Boolean x, Boolean y) -> !x || y);
    registrar.add("greater_bool", Boolean.class, Boolean.class, (Boolean x, Boolean y) -> x && !y);
    registrar.add(
        "greater_equals_bool", Boolean.class, Boolean.class, (Boolean x, Boolean y) -> x || !y);
  }

  private static void addBytesFunctions(Registrar registrar) {
    // Identity
    registrar.add("bytes_to_bytes", ByteString.class, (ByteString x) -> x);
    // Bytes ordering functions: <, <=, >=, >
    registrar.add(
        "less_bytes",
        ByteString.class,
        ByteString.class,
        (ByteString x, ByteString y) ->
            ByteString.unsignedLexicographicalComparator().compare(x, y) < 0);
    registrar.add(
        "less_equals_bytes",
        ByteString.class,
        ByteString.class,
        (ByteString x, ByteString y) ->
            ByteString.unsignedLexicographicalComparator().compare(x, y) <= 0);
    registrar.add(
        "greater_bytes",
        ByteString.class,
        ByteString.class,
        (ByteString x, ByteString y) ->
            ByteString.unsignedLexicographicalComparator().compare(x, y) > 0);
    registrar.add(
        "greater_equals_bytes",
        ByteString.class,
        ByteString.class,
        (ByteString x, ByteString y) ->
            ByteString.unsignedLexicographicalComparator().compare(x, y) >= 0);

    // Concatenation.
    registrar.add("add_bytes", ByteString.class, ByteString.class, ByteString::concat);

    // Global and receiver functions for size(bytes) and bytes.size() respectively.
    registrar.add("size_bytes", ByteString.class, (ByteString bytes) -> (long) bytes.size());
    registrar.add("bytes_size", ByteString.class, (ByteString bytes) -> (long) bytes.size());

    // Conversion functions.
    registrar.add("string_to_bytes", String.class, ByteString::copyFromUtf8);
  }

  private static void addDoubleFunctions(Registrar registrar, CelOptions celOptions) {
    // Identity
    registrar.add("double_to_double", Double.class, (Double x) -> x);
    // Double ordering functions.
    registrar.add("less_double", Double.class, Double.class, (Double x, Double y) -> x < y);
    registrar.add("less_equals_double", Double.class, Double.class, (Double x, Double y) -> x <= y);
    registrar.add("greater_double", Double.class, Double.class, (Double x, Double y) -> x > y);
    registrar.add(
        "greater_equals_double", Double.class, Double.class, (Double x, Double y) -> x >= y);

    // Double arithmetic operations.
    registrar.add("add_double", Double.class, Double.class, (Double x, Double y) -> x + y);
    registrar.add("subtract_double", Double.class, Double.class, (Double x, Double y) -> x - y);
    registrar.add("multiply_double", Double.class, Double.class, (Double x, Double y) -> x * y);
    registrar.add("divide_double", Double.class, Double.class, (Double x, Double y) -> x / y);
    registrar.add("negate_double", Double.class, (Double x) -> -x);

    // Conversions to double.
    registrar.add("int64_to_double", Long.class, Long::doubleValue);
    if (celOptions.enableUnsignedLongs()) {
      registrar.add("uint64_to_double", UnsignedLong.class, UnsignedLong::doubleValue);
    } else {
      registrar.add(
          "uint64_to_double",
          Long.class,
          (Long arg) -> UnsignedLong.fromLongBits(arg).doubleValue());
    }
    registrar.add(
        "string_to_double",
        String.class,
        (String arg) -> {
          try {
            return Double.parseDouble(arg);
          } catch (NumberFormatException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(CelErrorCode.BAD_FORMAT)
                .build();
          }
        });
  }

  private static void addDurationFunctions(Registrar registrar) {
    // Identity
    registrar.add("duration_to_duration", Duration.class, (Duration x) -> x);
    // Duration ordering functions: <, <=, >=, >
    registrar.add(
        "less_duration",
        Duration.class,
        Duration.class,
        (Duration x, Duration y) -> Durations.compare(x, y) < 0);
    registrar.add(
        "less_equals_duration",
        Duration.class,
        Duration.class,
        (Duration x, Duration y) -> Durations.compare(x, y) <= 0);
    registrar.add(
        "greater_duration",
        Duration.class,
        Duration.class,
        (Duration x, Duration y) -> Durations.compare(x, y) > 0);
    registrar.add(
        "greater_equals_duration",
        Duration.class,
        Duration.class,
        (Duration x, Duration y) -> Durations.compare(x, y) >= 0);

    // Duration arithmetic functions. Some functions which involve a timestamp and duration
    // can be found in the `addTimestampFunctions`.
    registrar.add("add_duration_duration", Duration.class, Duration.class, Durations::add);
    registrar.add(
        "subtract_duration_duration", Duration.class, Duration.class, Durations::subtract);

    // Type conversion functions.
    registrar.add(
        "string_to_duration",
        String.class,
        (String d) -> {
          try {
            return RuntimeHelpers.createDurationFromString(d);
          } catch (IllegalArgumentException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setErrorCode(CelErrorCode.BAD_FORMAT)
                .build();
          }
        });

    // Functions for extracting different time components and units from a duration.

    // getHours
    registrar.add("duration_to_hours", Duration.class, Durations::toHours);

    // getMinutes
    registrar.add("duration_to_minutes", Duration.class, Durations::toMinutes);

    // getSeconds
    registrar.add("duration_to_seconds", Duration.class, Durations::toSeconds);

    // getMilliseconds
    // duration as milliseconds and not just the millisecond part of a duration.
    registrar.add(
        "duration_to_milliseconds",
        Duration.class,
        (Duration arg) -> Durations.toMillis(arg) % ofSeconds(1).toMillis());
  }

  private static void addIntFunctions(Registrar registrar, CelOptions celOptions) {
    // Identity
    if (celOptions.enableUnsignedLongs()) {
      // Note that we require UnsignedLong flag here to avoid ambiguous overloads against
      // "uint64_to_int64", because they both use the same Java Long class.
      registrar.add("int64_to_int64", Long.class, (Long x) -> x);
    }
    // Comparison functions.
    registrar.add("less_int64", Long.class, Long.class, (Long x, Long y) -> x < y);
    registrar.add("less_equals_int64", Long.class, Long.class, (Long x, Long y) -> x <= y);
    registrar.add("greater_int64", Long.class, Long.class, (Long x, Long y) -> x > y);
    registrar.add("greater_equals_int64", Long.class, Long.class, (Long x, Long y) -> x >= y);

    // Arithmetic functions.
    registrar.add(
        "add_int64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return RuntimeHelpers.int64Add(x, y, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "subtract_int64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return RuntimeHelpers.int64Subtract(x, y, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "multiply_int64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return RuntimeHelpers.int64Multiply(x, y, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "divide_int64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return RuntimeHelpers.int64Divide(x, y, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "modulo_int64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return x % y;
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "negate_int64",
        Long.class,
        (Long x) -> {
          try {
            return RuntimeHelpers.int64Negate(x, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });

    // Conversions to int
    if (celOptions.enableUnsignedLongs()) {
      registrar.add(
          "uint64_to_int64",
          UnsignedLong.class,
          (UnsignedLong arg) -> {
            if (arg.compareTo(UnsignedLong.valueOf(Long.MAX_VALUE)) > 0) {
              throw new InterpreterException.Builder("unsigned out of int range")
                  .setErrorCode(CelErrorCode.NUMERIC_OVERFLOW)
                  .build();
            }
            return arg.longValue();
          });
    } else {
      registrar.add(
          "uint64_to_int64",
          Long.class,
          (Long arg) -> {
            if (celOptions.errorOnIntWrap() && arg < 0) {
              throw new InterpreterException.Builder("unsigned out of int range")
                  .setErrorCode(CelErrorCode.NUMERIC_OVERFLOW)
                  .build();
            }
            return arg;
          });
    }
    registrar.add(
        "double_to_int64",
        Double.class,
        (Double arg) -> {
          if (celOptions.errorOnIntWrap()) {
            return RuntimeHelpers.doubleToLongChecked(arg)
                .orElseThrow(
                    () ->
                        new InterpreterException.Builder("double is out of range for int")
                            .setErrorCode(CelErrorCode.NUMERIC_OVERFLOW)
                            .build());
          }
          return arg.longValue();
        });
    registrar.add(
        "string_to_int64",
        String.class,
        (String arg) -> {
          try {
            return Long.parseLong(arg);
          } catch (NumberFormatException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(CelErrorCode.BAD_FORMAT)
                .build();
          }
        });
    registrar.add("timestamp_to_int64", Timestamp.class, Timestamps::toSeconds);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void addListFunctions(
      Registrar registrar, RuntimeEquality runtimeEquality, CelOptions celOptions) {
    // List concatenation.
    registrar.add("add_list", List.class, List.class, RuntimeHelpers::concat);

    // List indexing, a[b]
    registrar.add("index_list", List.class, Number.class, RuntimeHelpers::indexList);

    // Global and receiver overloads for size(list) and list.size() respectively.
    registrar.add("size_list", List.class, (List list1) -> (long) list1.size());
    registrar.add("list_size", List.class, (List list1) -> (long) list1.size());

    // TODO: Deprecate in(a, b).
    // In function: in(a, b)
    registrar.add(
        "in_function_list",
        List.class,
        Object.class,
        (List list, Object value) -> runtimeEquality.inList(list, value, celOptions));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void addMapFunctions(
      Registrar registrar, RuntimeEquality runtimeEquality, CelOptions celOptions) {
    // Map indexing, a[b]
    registrar.add(
        "index_map",
        Map.class,
        Object.class,
        (Map map, Object key) -> runtimeEquality.indexMap(map, key, celOptions));

    // Global and receiver overloads for size(map) and map.size() respectively.
    registrar.add("size_map", Map.class, (Map map1) -> (long) map1.size());
    registrar.add("map_size", Map.class, (Map map1) -> (long) map1.size());

    // TODO: Deprecate in(a, b).
    registrar.add(
        "in_function_map",
        Map.class,
        Object.class,
        (Map map, Object key) -> runtimeEquality.inMap(map, key, celOptions));
  }

  private static void addStringFunctions(Registrar registrar, CelOptions celOptions) {
    // Identity
    registrar.add("string_to_string", String.class, (String x) -> x);
    // String ordering functions: <, <=, >=, >.
    registrar.add(
        "less_string", String.class, String.class, (String x, String y) -> x.compareTo(y) < 0);
    registrar.add(
        "less_equals_string",
        String.class,
        String.class,
        (String x, String y) -> x.compareTo(y) <= 0);
    registrar.add(
        "greater_string", String.class, String.class, (String x, String y) -> x.compareTo(y) > 0);
    registrar.add(
        "greater_equals_string",
        String.class,
        String.class,
        (String x, String y) -> x.compareTo(y) >= 0);

    // String concatenation.
    registrar.add("add_string", String.class, String.class, (String x, String y) -> x + y);

    // Global and receiver function for size(string) and string.size() respectively.
    registrar.add(
        "size_string", String.class, (String s) -> (long) s.codePointCount(0, s.length()));
    registrar.add(
        "string_size", String.class, (String s) -> (long) s.codePointCount(0, s.length()));

    // String operation functions. There's a 'match' function which is part of this set, but is
    // declared elsewhere as some implementations special case it.
    registrar.add("contains_string", String.class, String.class, String::contains);
    registrar.add("ends_with_string", String.class, String.class, String::endsWith);
    registrar.add("starts_with_string", String.class, String.class, String::startsWith);

    // Conversions to string.
    registrar.add("int64_to_string", Long.class, (Long arg) -> arg.toString());
    if (celOptions.enableUnsignedLongs()) {
      registrar.add("uint64_to_string", UnsignedLong.class, UnsignedLong::toString);
    } else {
      registrar.add("uint64_to_string", Long.class, UnsignedLongs::toString);
    }
    registrar.add("double_to_string", Double.class, (Double arg) -> arg.toString());
    registrar.add("bytes_to_string", ByteString.class, ByteString::toStringUtf8);
    registrar.add("timestamp_to_string", Timestamp.class, Timestamps::toString);
    registrar.add("duration_to_string", Duration.class, Durations::toString);
  }

  // We specifically need to only access nanos-of-second field for
  // timestamp_to_milliseconds overload
  @SuppressWarnings("JavaLocalDateTimeGetNano")
  private static void addTimestampFunctions(Registrar registrar) {
    // Identity
    registrar.add("timestamp_to_timestamp", Timestamp.class, (Timestamp x) -> x);
    // Timestamp relation operators: <, <=, >=, >
    registrar.add(
        "less_timestamp",
        Timestamp.class,
        Timestamp.class,
        (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) < 0);
    registrar.add(
        "less_equals_timestamp",
        Timestamp.class,
        Timestamp.class,
        (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) <= 0);
    registrar.add(
        "greater_timestamp",
        Timestamp.class,
        Timestamp.class,
        (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) > 0);
    registrar.add(
        "greater_equals_timestamp",
        Timestamp.class,
        Timestamp.class,
        (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) >= 0);

    // Timestamp and timestamp/duration arithmetic operators.
    registrar.add("add_timestamp_duration", Timestamp.class, Duration.class, Timestamps::add);
    registrar.add(
        "add_duration_timestamp",
        Duration.class,
        Timestamp.class,
        (Duration x, Timestamp y) -> Timestamps.add(y, x));
    registrar.add(
        "subtract_timestamp_timestamp",
        Timestamp.class,
        Timestamp.class,
        (Timestamp x, Timestamp y) -> Timestamps.between(y, x));
    registrar.add(
        "subtract_timestamp_duration", Timestamp.class, Duration.class, Timestamps::subtract);

    // Conversions to timestamp.
    registrar.add(
        "string_to_timestamp",
        String.class,
        (String ts) -> {
          try {
            return Timestamps.parse(ts);
          } catch (ParseException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setErrorCode(CelErrorCode.BAD_FORMAT)
                .build();
          }
        });
    registrar.add("int64_to_timestamp", Long.class, Timestamps::fromSeconds);

    // Date/time functions
    // getFullYear
    registrar.add(
        "timestamp_to_year",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getYear());
    registrar.add(
        "timestamp_to_year_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getYear());

    // getMonth
    registrar.add(
        "timestamp_to_month",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getMonthValue() - 1);
    registrar.add(
        "timestamp_to_month_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getMonthValue() - 1);

    // getDayOfYear
    registrar.add(
        "timestamp_to_day_of_year",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfYear() - 1);
    registrar.add(
        "timestamp_to_day_of_year_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfYear() - 1);

    // getDayOfMonth
    registrar.add(
        "timestamp_to_day_of_month",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth() - 1);
    registrar.add(
        "timestamp_to_day_of_month_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth() - 1);

    // getDate
    registrar.add(
        "timestamp_to_day_of_month_1_based",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth());
    registrar.add(
        "timestamp_to_day_of_month_1_based_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth());

    // getDayOfWeek
    registrar.add(
        "timestamp_to_day_of_week",
        Timestamp.class,
        (Timestamp ts) -> {
          // CEL treats Sunday as day 0, but Java.time treats it as day 7.
          DayOfWeek dayOfWeek = newLocalDateTime(ts, UTC).getDayOfWeek();
          return (long) dayOfWeek.getValue() % 7;
        });
    registrar.add(
        "timestamp_to_day_of_week_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> {
          // CEL treats Sunday as day 0, but Java.time treats it as day 7.
          DayOfWeek dayOfWeek = newLocalDateTime(ts, tz).getDayOfWeek();
          return (long) dayOfWeek.getValue() % 7;
        });

    // getHours
    registrar.add(
        "timestamp_to_hours",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getHour());
    registrar.add(
        "timestamp_to_hours_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getHour());
    registrar.add(
        "timestamp_to_minutes",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getMinute());
    registrar.add(
        "timestamp_to_minutes_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getMinute());
    registrar.add(
        "timestamp_to_seconds",
        Timestamp.class,
        (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getSecond());
    registrar.add(
        "timestamp_to_seconds_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getSecond());
    registrar.add(
        "timestamp_to_milliseconds",
        Timestamp.class,
        (Timestamp ts) -> (long) (newLocalDateTime(ts, UTC).getNano() / 1e+6));
    registrar.add(
        "timestamp_to_milliseconds_with_tz",
        Timestamp.class,
        String.class,
        (Timestamp ts, String tz) -> (long) (newLocalDateTime(ts, tz).getNano() / 1e+6));
  }

  private static void addSignedUintFunctions(Registrar registrar, CelOptions celOptions) {
    // Identity
    registrar.add("uint64_to_uint64", Long.class, (Long x) -> x);
    // Uint relation operators: <, <=, >=, >
    registrar.add(
        "less_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> RuntimeHelpers.uint64CompareTo(x, y, celOptions) < 0);
    registrar.add(
        "less_equals_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> RuntimeHelpers.uint64CompareTo(x, y, celOptions) <= 0);
    registrar.add(
        "greater_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> RuntimeHelpers.uint64CompareTo(x, y, celOptions) > 0);
    registrar.add(
        "greater_equals_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> RuntimeHelpers.uint64CompareTo(x, y, celOptions) >= 0);

    // Uint arithmetic operators.
    registrar.add(
        "add_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return RuntimeHelpers.uint64Add(x, y, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "subtract_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return RuntimeHelpers.uint64Subtract(x, y, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "multiply_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> {
          try {
            return RuntimeHelpers.uint64Multiply(x, y, celOptions);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "divide_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> RuntimeHelpers.uint64Divide(x, y, celOptions));
    registrar.add(
        "modulo_uint64",
        Long.class,
        Long.class,
        (Long x, Long y) -> RuntimeHelpers.uint64Mod(x, y, celOptions));

    // Conversions to uint.
    registrar.add(
        "int64_to_uint64",
        Long.class,
        (Long arg) -> {
          if (celOptions.errorOnIntWrap() && arg < 0) {
            throw new InterpreterException.Builder("int out of uint range")
                .setErrorCode(CelErrorCode.NUMERIC_OVERFLOW)
                .build();
          }
          return arg;
        });
    registrar.add(
        "double_to_uint64",
        Double.class,
        (Double arg) -> {
          if (celOptions.errorOnIntWrap()) {
            return RuntimeHelpers.doubleToUnsignedChecked(arg)
                .map(UnsignedLong::longValue)
                .orElseThrow(
                    () ->
                        new InterpreterException.Builder("double out of uint range")
                            .setErrorCode(CelErrorCode.NUMERIC_OVERFLOW)
                            .build());
          }
          return arg.longValue();
        });
    registrar.add(
        "string_to_uint64",
        String.class,
        (String arg) -> {
          try {
            return UnsignedLongs.parseUnsignedLong(arg);
          } catch (NumberFormatException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(CelErrorCode.BAD_FORMAT)
                .build();
          }
        });
  }

  private static void addUintFunctions(Registrar registrar, CelOptions celOptions) {
    // Identity
    registrar.add("uint64_to_uint64", UnsignedLong.class, (UnsignedLong x) -> x);
    registrar.add(
        "less_uint64",
        UnsignedLong.class,
        UnsignedLong.class,
        (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) < 0);
    registrar.add(
        "less_equals_uint64",
        UnsignedLong.class,
        UnsignedLong.class,
        (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) <= 0);
    registrar.add(
        "greater_uint64",
        UnsignedLong.class,
        UnsignedLong.class,
        (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) > 0);
    registrar.add(
        "greater_equals_uint64",
        UnsignedLong.class,
        UnsignedLong.class,
        (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) >= 0);

    registrar.add(
        "add_uint64",
        UnsignedLong.class,
        UnsignedLong.class,
        (UnsignedLong x, UnsignedLong y) -> {
          try {
            return RuntimeHelpers.uint64Add(x, y);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "subtract_uint64",
        UnsignedLong.class,
        UnsignedLong.class,
        (UnsignedLong x, UnsignedLong y) -> {
          try {
            return RuntimeHelpers.uint64Subtract(x, y);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "multiply_uint64",
        UnsignedLong.class,
        UnsignedLong.class,
        (UnsignedLong x, UnsignedLong y) -> {
          try {
            return RuntimeHelpers.uint64Multiply(x, y);
          } catch (ArithmeticException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(getArithmeticErrorCode(e))
                .build();
          }
        });
    registrar.add(
        "divide_uint64", UnsignedLong.class, UnsignedLong.class, RuntimeHelpers::uint64Divide);

    // Modulo
    registrar.add(
        "modulo_uint64", UnsignedLong.class, UnsignedLong.class, RuntimeHelpers::uint64Mod);

    // Conversions to uint.
    registrar.add(
        "int64_to_uint64",
        Long.class,
        (Long arg) -> {
          if (celOptions.errorOnIntWrap() && arg < 0) {
            throw new InterpreterException.Builder("int out of uint range")
                .setErrorCode(CelErrorCode.NUMERIC_OVERFLOW)
                .build();
          }
          return UnsignedLong.valueOf(arg);
        });
    registrar.add(
        "double_to_uint64",
        Double.class,
        (Double arg) -> {
          if (celOptions.errorOnIntWrap()) {
            return RuntimeHelpers.doubleToUnsignedChecked(arg)
                .orElseThrow(
                    () ->
                        new InterpreterException.Builder("double out of uint range")
                            .setErrorCode(CelErrorCode.NUMERIC_OVERFLOW)
                            .build());
          }
          return UnsignedLong.valueOf(BigDecimal.valueOf(arg).toBigInteger());
        });
    registrar.add(
        "string_to_uint64",
        String.class,
        (String arg) -> {
          try {
            return UnsignedLong.valueOf(arg);
          } catch (NumberFormatException e) {
            throw new InterpreterException.Builder(e.getMessage())
                .setCause(e)
                .setErrorCode(CelErrorCode.BAD_FORMAT)
                .build();
          }
        });
  }

  private static void addCrossTypeNumericFunctions(Registrar registrar) {
    // Cross-type numeric less than.
    registrar.add(
        "less_int64_uint64",
        Long.class,
        UnsignedLong.class,
        (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) == -1);
    registrar.add(
        "less_uint64_int64",
        UnsignedLong.class,
        Long.class,
        (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) == -1);
    registrar.add(
        "less_int64_double",
        Long.class,
        Double.class,
        (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) == -1);
    registrar.add(
        "less_double_int64",
        Double.class,
        Long.class,
        (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) == -1);
    registrar.add(
        "less_uint64_double",
        UnsignedLong.class,
        Double.class,
        (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) == -1);
    registrar.add(
        "less_double_uint64",
        Double.class,
        UnsignedLong.class,
        (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) == -1);
    // Cross-type numeric less than or equal.
    registrar.add(
        "less_equals_int64_uint64",
        Long.class,
        UnsignedLong.class,
        (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) <= 0);
    registrar.add(
        "less_equals_uint64_int64",
        UnsignedLong.class,
        Long.class,
        (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) <= 0);
    registrar.add(
        "less_equals_int64_double",
        Long.class,
        Double.class,
        (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) <= 0);
    registrar.add(
        "less_equals_double_int64",
        Double.class,
        Long.class,
        (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) <= 0);
    registrar.add(
        "less_equals_uint64_double",
        UnsignedLong.class,
        Double.class,
        (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) <= 0);
    registrar.add(
        "less_equals_double_uint64",
        Double.class,
        UnsignedLong.class,
        (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) <= 0);
    // Cross-type numeric greater than.
    registrar.add(
        "greater_int64_uint64",
        Long.class,
        UnsignedLong.class,
        (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) == 1);
    registrar.add(
        "greater_uint64_int64",
        UnsignedLong.class,
        Long.class,
        (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) == 1);
    registrar.add(
        "greater_int64_double",
        Long.class,
        Double.class,
        (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) == 1);
    registrar.add(
        "greater_double_int64",
        Double.class,
        Long.class,
        (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) == 1);
    registrar.add(
        "greater_uint64_double",
        UnsignedLong.class,
        Double.class,
        (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) == 1);
    registrar.add(
        "greater_double_uint64",
        Double.class,
        UnsignedLong.class,
        (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) == 1);
    // Cross-type numeric greater than or equal.
    registrar.add(
        "greater_equals_int64_uint64",
        Long.class,
        UnsignedLong.class,
        (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) >= 0);
    registrar.add(
        "greater_equals_uint64_int64",
        UnsignedLong.class,
        Long.class,
        (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) >= 0);
    registrar.add(
        "greater_equals_int64_double",
        Long.class,
        Double.class,
        (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) >= 0);
    registrar.add(
        "greater_equals_double_int64",
        Double.class,
        Long.class,
        (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) >= 0);
    registrar.add(
        "greater_equals_uint64_double",
        UnsignedLong.class,
        Double.class,
        (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) >= 0);
    registrar.add(
        "greater_equals_double_uint64",
        Double.class,
        UnsignedLong.class,
        (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) >= 0);
  }

  /**
   * Note: These aren't part of the standard language definitions, but it is being defined here to
   * support runtime bindings for CelOptionalLibrary, as it requires specific dependencies such as
   * {@link RuntimeEquality} that is only available here.
   *
   * <p>Conversely, declarations related to Optional values should NOT be added as part of the
   * standard definitions to avoid accidental exposure of this optional feature.
   */
  @SuppressWarnings({"rawtypes"})
  private static void addOptionalValueFunctions(
      Registrar registrar, RuntimeEquality runtimeEquality, CelOptions options) {
    registrar.add(
        "select_optional_field", // This only handles map selection. Proto selection is special
        // cased inside the interpreter.
        Map.class,
        String.class,
        (Map map, String key) -> runtimeEquality.findInMap(map, key, options));
    registrar.add(
        "map_optindex_optional_value",
        Map.class,
        Object.class,
        (Map map, Object key) -> runtimeEquality.findInMap(map, key, options));
    registrar.add(
        "optional_map_optindex_optional_value",
        Optional.class,
        Object.class,
        (Optional optionalMap, Object key) ->
            indexOptionalMap(optionalMap, key, options, runtimeEquality));
    registrar.add(
        "optional_map_index_value",
        Optional.class,
        Object.class,
        (Optional optionalMap, Object key) ->
            indexOptionalMap(optionalMap, key, options, runtimeEquality));
    registrar.add(
        "optional_list_index_int",
        Optional.class,
        Long.class,
        StandardFunctions::indexOptionalList);
    registrar.add(
        "list_optindex_optional_int",
        List.class,
        Long.class,
        (List list, Long index) -> {
          int castIndex = Ints.checkedCast(index);
          if (castIndex < 0 || castIndex >= list.size()) {
            return Optional.empty();
          }
          return Optional.of(list.get(castIndex));
        });
    registrar.add(
        "optional_list_optindex_optional_int",
        Optional.class,
        Long.class,
        StandardFunctions::indexOptionalList);
  }

  private static Object indexOptionalMap(
      Optional<?> optionalMap, Object key, CelOptions options, RuntimeEquality runtimeEquality) {
    if (!optionalMap.isPresent()) {
      return Optional.empty();
    }

    Map<?, ?> map = (Map<?, ?>) optionalMap.get();

    return runtimeEquality.findInMap(map, key, options);
  }

  private static Object indexOptionalList(Optional<?> optionalList, long index) {
    if (!optionalList.isPresent()) {
      return Optional.empty();
    }
    List<?> list = (List<?>) optionalList.get();
    int castIndex = Ints.checkedCast(index);
    if (castIndex < 0 || castIndex >= list.size()) {
      return Optional.empty();
    }
    return Optional.of(list.get(castIndex));
  }

  /**
   * Get the DateTimeZone Instance.
   *
   * @param tz the ID of the datetime zone
   * @return the ZoneId object
   * @throws InterpreterException if there is an invalid timezone
   */
  private static ZoneId timeZone(String tz) throws InterpreterException {
    try {
      return ZoneId.of(tz);
    } catch (DateTimeException e) {
      // If timezone is not a string name (for example, 'US/Central'), it should be a numerical
      // offset from UTC in the format [+/-]HH:MM.
      try {
        int ind = tz.indexOf(":");
        if (ind == -1) {
          throw new InterpreterException.Builder(e.getMessage()).build();
        }

        int hourOffset = Integer.parseInt(tz.substring(0, ind));
        int minOffset = Integer.parseInt(tz.substring(ind + 1));
        // Ensures that the offset are properly formatted in [+/-]HH:MM to conform with
        // ZoneOffset's format requirements.
        // Example: "-9:30" -> "-09:30" and "9:30" -> "+09:30"
        String formattedOffset =
            ((hourOffset < 0) ? "-" : "+")
                + String.format("%02d:%02d", Math.abs(hourOffset), minOffset);

        return ZoneId.of(formattedOffset);

      } catch (DateTimeException e2) {
        throw new InterpreterException.Builder(e2.getMessage()).build();
      }
    }
  }

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
  private static LocalDateTime newLocalDateTime(Timestamp ts, String tz)
      throws InterpreterException {
    return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())
        .atZone(timeZone(tz))
        .toLocalDateTime();
  }

  private static CelErrorCode getArithmeticErrorCode(ArithmeticException e) {
    String exceptionMessage = e.getMessage();
    // The two known cases for an arithmetic exception is divide by zero and overflow.
    if (exceptionMessage.equals("/ by zero")) {
      return CelErrorCode.DIVIDE_BY_ZERO;
    }
    return CelErrorCode.NUMERIC_OVERFLOW;
  }

  private StandardFunctions() {}
}
