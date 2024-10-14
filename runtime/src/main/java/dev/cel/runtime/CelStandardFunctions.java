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

package dev.cel.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedLong;
import com.google.common.primitives.UnsignedLongs;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import com.google.re2j.PatternSyntaxException;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.common.internal.DynamicProto;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Arithmetic;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.BooleanOperator;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Comparison;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Conversions;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.DateTime;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Index;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.InternalOperator;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Relation;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Size;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.StringMatchers;
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

/** Runtime function bindings for the standard functions in CEL. */
@Immutable
public final class CelStandardFunctions {
  private static final String UTC = "UTC";

  private final ImmutableSet<StandardOverload> standardOverloads;

  /**
   * Enumeration of Standard Function bindings.
   *
   * <p>Note: The conditional, logical_or, logical_and, not_strictly_false, and type functions are
   * currently special-cased, and does not appear in this enum.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public enum StandardFunction {
    LOGICAL_NOT(BooleanOperator.LOGICAL_NOT),
    IN(InternalOperator.IN_LIST, InternalOperator.IN_MAP),
    EQUALS(Relation.EQUALS),
    NOT_EQUALS(Relation.NOT_EQUALS),
    BOOL(Conversions.BOOL_TO_BOOL, Conversions.STRING_TO_BOOL),
    ADD(
        Arithmetic.ADD_INT64,
        Arithmetic.ADD_UINT64,
        Arithmetic.ADD_DOUBLE,
        Arithmetic.ADD_STRING,
        Arithmetic.ADD_BYTES,
        Arithmetic.ADD_LIST,
        Arithmetic.ADD_TIMESTAMP_DURATION,
        Arithmetic.ADD_DURATION_TIMESTAMP,
        Arithmetic.ADD_DURATION_DURATION),
    SUBTRACT(
        Arithmetic.SUBTRACT_INT64,
        Arithmetic.SUBTRACT_TIMESTAMP_TIMESTAMP,
        Arithmetic.SUBTRACT_TIMESTAMP_DURATION,
        Arithmetic.SUBTRACT_UINT64,
        Arithmetic.SUBTRACT_DOUBLE,
        Arithmetic.SUBTRACT_DURATION_DURATION),
    MULTIPLY(Arithmetic.MULTIPLY_INT64, Arithmetic.MULTIPLY_DOUBLE, Arithmetic.MULTIPLY_UINT64),
    DIVIDE(Arithmetic.DIVIDE_DOUBLE, Arithmetic.DIVIDE_INT64, Arithmetic.DIVIDE_UINT64),
    MODULO(Arithmetic.MODULO_INT64, Arithmetic.MODULO_UINT64),
    NEGATE(Arithmetic.NEGATE_INT64, Arithmetic.NEGATE_DOUBLE),
    INDEX(Index.INDEX_LIST, Index.INDEX_MAP),
    SIZE(
        Size.SIZE_STRING,
        Size.SIZE_BYTES,
        Size.SIZE_LIST,
        Size.SIZE_MAP,
        Size.STRING_SIZE,
        Size.BYTES_SIZE,
        Size.LIST_SIZE,
        Size.MAP_SIZE),
    INT(
        Conversions.INT64_TO_INT64,
        Conversions.UINT64_TO_INT64,
        Conversions.DOUBLE_TO_INT64,
        Conversions.STRING_TO_INT64,
        Conversions.TIMESTAMP_TO_INT64),
    UINT(
        Conversions.UINT64_TO_UINT64,
        Conversions.INT64_TO_UINT64,
        Conversions.DOUBLE_TO_UINT64,
        Conversions.STRING_TO_UINT64),
    DOUBLE(
        Conversions.DOUBLE_TO_DOUBLE,
        Conversions.INT64_TO_DOUBLE,
        Conversions.STRING_TO_DOUBLE,
        Conversions.UINT64_TO_DOUBLE),
    STRING(
        Conversions.STRING_TO_STRING,
        Conversions.INT64_TO_STRING,
        Conversions.DOUBLE_TO_STRING,
        Conversions.BYTES_TO_STRING,
        Conversions.TIMESTAMP_TO_STRING,
        Conversions.DURATION_TO_STRING,
        Conversions.UINT64_TO_STRING),
    BYTES(Conversions.BYTES_TO_BYTES, Conversions.STRING_TO_BYTES),
    DURATION(Conversions.DURATION_TO_DURATION, Conversions.STRING_TO_DURATION),
    TIMESTAMP(
        Conversions.STRING_TO_TIMESTAMP,
        Conversions.TIMESTAMP_TO_TIMESTAMP,
        Conversions.INT64_TO_TIMESTAMP),
    DYN(Conversions.TO_DYN),
    MATCHES(StringMatchers.MATCHES, StringMatchers.MATCHES_STRING),
    CONTAINS(StringMatchers.CONTAINS_STRING),
    ENDS_WITH(StringMatchers.ENDS_WITH_STRING),
    STARTS_WITH(StringMatchers.STARTS_WITH_STRING),
    // Date/time Functions
    GET_FULL_YEAR(DateTime.TIMESTAMP_TO_YEAR, DateTime.TIMESTAMP_TO_YEAR_WITH_TZ),
    GET_MONTH(DateTime.TIMESTAMP_TO_MONTH, DateTime.TIMESTAMP_TO_MONTH_WITH_TZ),
    GET_DAY_OF_YEAR(DateTime.TIMESTAMP_TO_DAY_OF_YEAR, DateTime.TIMESTAMP_TO_DAY_OF_YEAR_WITH_TZ),
    GET_DAY_OF_MONTH(
        DateTime.TIMESTAMP_TO_DAY_OF_MONTH, DateTime.TIMESTAMP_TO_DAY_OF_MONTH_WITH_TZ),
    GET_DATE(
        DateTime.TIMESTAMP_TO_DAY_OF_MONTH_1_BASED,
        DateTime.TIMESTAMP_TO_DAY_OF_MONTH_1_BASED_WITH_TZ),
    GET_DAY_OF_WEEK(DateTime.TIMESTAMP_TO_DAY_OF_WEEK, DateTime.TIMESTAMP_TO_DAY_OF_WEEK_WITH_TZ),

    GET_HOURS(
        DateTime.TIMESTAMP_TO_HOURS,
        DateTime.TIMESTAMP_TO_HOURS_WITH_TZ,
        DateTime.DURATION_TO_HOURS),
    GET_MINUTES(
        DateTime.TIMESTAMP_TO_MINUTES,
        DateTime.TIMESTAMP_TO_MINUTES_WITH_TZ,
        DateTime.DURATION_TO_MINUTES),
    GET_SECONDS(
        DateTime.TIMESTAMP_TO_SECONDS,
        DateTime.TIMESTAMP_TO_SECONDS_WITH_TZ,
        DateTime.DURATION_TO_SECONDS),
    GET_MILLISECONDS(
        DateTime.TIMESTAMP_TO_MILLISECONDS,
        DateTime.TIMESTAMP_TO_MILLISECONDS_WITH_TZ,
        DateTime.DURATION_TO_MILLISECONDS),
    LESS(
        Comparison.LESS_BOOL,
        Comparison.LESS_INT64,
        Comparison.LESS_UINT64,
        Comparison.LESS_DOUBLE,
        Comparison.LESS_STRING,
        Comparison.LESS_BYTES,
        Comparison.LESS_TIMESTAMP,
        Comparison.LESS_DURATION,
        Comparison.LESS_INT64_UINT64,
        Comparison.LESS_UINT64_INT64,
        Comparison.LESS_INT64_DOUBLE,
        Comparison.LESS_DOUBLE_INT64,
        Comparison.LESS_UINT64_DOUBLE,
        Comparison.LESS_DOUBLE_UINT64),
    LESS_EQUALS(
        Comparison.LESS_EQUALS_BOOL,
        Comparison.LESS_EQUALS_INT64,
        Comparison.LESS_EQUALS_UINT64,
        Comparison.LESS_EQUALS_DOUBLE,
        Comparison.LESS_EQUALS_STRING,
        Comparison.LESS_EQUALS_BYTES,
        Comparison.LESS_EQUALS_TIMESTAMP,
        Comparison.LESS_EQUALS_DURATION,
        Comparison.LESS_EQUALS_INT64_UINT64,
        Comparison.LESS_EQUALS_UINT64_INT64,
        Comparison.LESS_EQUALS_INT64_DOUBLE,
        Comparison.LESS_EQUALS_DOUBLE_INT64,
        Comparison.LESS_EQUALS_UINT64_DOUBLE,
        Comparison.LESS_EQUALS_DOUBLE_UINT64),
    GREATER(
        Comparison.GREATER_BOOL,
        Comparison.GREATER_INT64,
        Comparison.GREATER_UINT64,
        Comparison.GREATER_DOUBLE,
        Comparison.GREATER_STRING,
        Comparison.GREATER_BYTES,
        Comparison.GREATER_TIMESTAMP,
        Comparison.GREATER_DURATION,
        Comparison.GREATER_INT64_UINT64,
        Comparison.GREATER_UINT64_INT64,
        Comparison.GREATER_INT64_DOUBLE,
        Comparison.GREATER_DOUBLE_INT64,
        Comparison.GREATER_UINT64_DOUBLE,
        Comparison.GREATER_DOUBLE_UINT64),
    GREATER_EQUALS(
        Comparison.GREATER_EQUALS_BOOL,
        Comparison.GREATER_EQUALS_BYTES,
        Comparison.GREATER_EQUALS_DOUBLE,
        Comparison.GREATER_EQUALS_DURATION,
        Comparison.GREATER_EQUALS_INT64,
        Comparison.GREATER_EQUALS_STRING,
        Comparison.GREATER_EQUALS_TIMESTAMP,
        Comparison.GREATER_EQUALS_UINT64,
        Comparison.GREATER_EQUALS_INT64_UINT64,
        Comparison.GREATER_EQUALS_UINT64_INT64,
        Comparison.GREATER_EQUALS_INT64_DOUBLE,
        Comparison.GREATER_EQUALS_DOUBLE_INT64,
        Comparison.GREATER_EQUALS_UINT64_DOUBLE,
        Comparison.GREATER_EQUALS_DOUBLE_UINT64),
    OPTIONAL(
        Overload.OptionalValue.SELECT_OPTIONAL_FIELD,
        Overload.OptionalValue.MAP_OPTINDEX_OPTIONAL_VALUE,
        Overload.OptionalValue.OPTIONAL_MAP_OPTINDEX_OPTIONAL_VALUE,
        Overload.OptionalValue.OPTIONAL_MAP_INDEX_VALUE,
        Overload.OptionalValue.OPTIONAL_LIST_INDEX_INT,
        Overload.OptionalValue.LIST_OPTINDEX_OPTIONAL_INT,
        Overload.OptionalValue.OPTIONAL_LIST_OPTINDEX_OPTIONAL_INT);

    /** Container class for CEL standard function overloads. */
    public static final class Overload {

      /** Overloads for internal functions that may have been rewritten by macros (ex: @in) */
      public enum InternalOperator implements StandardOverload {
        IN_LIST(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "in_list",
                    Object.class,
                    List.class,
                    (Object value, List list) ->
                        bindingHelper.runtimeEquality.inList(
                            list, value, bindingHelper.celOptions))),
        IN_MAP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "in_map",
                    Object.class,
                    Map.class,
                    (Object key, Map map) ->
                        bindingHelper.runtimeEquality.inMap(map, key, bindingHelper.celOptions)));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        InternalOperator(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for functions that test relations. */
      public enum Relation implements StandardOverload {
        EQUALS(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "equals",
                    Object.class,
                    Object.class,
                    (Object x, Object y) ->
                        bindingHelper.runtimeEquality.objectEquals(
                            x, y, bindingHelper.celOptions))),
        NOT_EQUALS(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "not_equals",
                    Object.class,
                    Object.class,
                    (Object x, Object y) ->
                        !bindingHelper.runtimeEquality.objectEquals(
                            x, y, bindingHelper.celOptions)));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        Relation(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for performing arithmetic operations. */
      public enum Arithmetic implements StandardOverload {
        ADD_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "add_int64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return RuntimeHelpers.int64Add(x, y, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    })),
        ADD_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "add_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    (UnsignedLong x, UnsignedLong y) -> {
                      try {
                        return RuntimeHelpers.uint64Add(x, y);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    });
              } else {
                return CelFunctionBinding.from(
                    "add_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return RuntimeHelpers.uint64Add(x, y, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    });
              }
            }),
        ADD_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "add_bytes", ByteString.class, ByteString.class, ByteString::concat)),
        ADD_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from("add_double", Double.class, Double.class, Double::sum)),
        ADD_DURATION_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "add_duration_duration", Duration.class, Duration.class, Durations::add)),
        ADD_TIMESTAMP_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "add_timestamp_duration", Timestamp.class, Duration.class, Timestamps::add)),
        ADD_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "add_string", String.class, String.class, (String x, String y) -> x + y)),
        ADD_DURATION_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "add_duration_timestamp",
                    Duration.class,
                    Timestamp.class,
                    (Duration x, Timestamp y) -> Timestamps.add(y, x))),
        ADD_LIST(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "add_list", List.class, List.class, RuntimeHelpers::concat)),
        SUBTRACT_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "subtract_int64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return RuntimeHelpers.int64Subtract(x, y, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    })),
        SUBTRACT_TIMESTAMP_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "subtract_timestamp_timestamp",
                    Timestamp.class,
                    Timestamp.class,
                    (Timestamp x, Timestamp y) -> Timestamps.between(y, x))),
        SUBTRACT_TIMESTAMP_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "subtract_timestamp_duration",
                    Timestamp.class,
                    Duration.class,
                    Timestamps::subtract)),
        SUBTRACT_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "subtract_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    (UnsignedLong x, UnsignedLong y) -> {
                      try {
                        return RuntimeHelpers.uint64Subtract(x, y);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    });
              } else {
                return CelFunctionBinding.from(
                    "subtract_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return RuntimeHelpers.uint64Subtract(x, y, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    });
              }
            }),
        SUBTRACT_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "subtract_double", Double.class, Double.class, (Double x, Double y) -> x - y)),
        SUBTRACT_DURATION_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "subtract_duration_duration",
                    Duration.class,
                    Duration.class,
                    Durations::subtract)),
        MULTIPLY_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "multiply_int64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return RuntimeHelpers.int64Multiply(x, y, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    })),
        MULTIPLY_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "multiply_double", Double.class, Double.class, (Double x, Double y) -> x * y)),
        MULTIPLY_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "multiply_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    (UnsignedLong x, UnsignedLong y) -> {
                      try {
                        return RuntimeHelpers.uint64Multiply(x, y);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    });
              } else {
                return CelFunctionBinding.from(
                    "multiply_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return RuntimeHelpers.uint64Multiply(x, y, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    });
              }
            }),
        DIVIDE_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "divide_double", Double.class, Double.class, (Double x, Double y) -> x / y)),
        DIVIDE_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "divide_int64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return RuntimeHelpers.int64Divide(x, y, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    })),
        DIVIDE_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "divide_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    RuntimeHelpers::uint64Divide);
              } else {
                return CelFunctionBinding.from(
                    "divide_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) ->
                        RuntimeHelpers.uint64Divide(x, y, bindingHelper.celOptions));
              }
            }),
        MODULO_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "modulo_int64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> {
                      try {
                        return x % y;
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    })),
        MODULO_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "modulo_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    RuntimeHelpers::uint64Mod);
              } else {
                return CelFunctionBinding.from(
                    "modulo_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) -> RuntimeHelpers.uint64Mod(x, y, bindingHelper.celOptions));
              }
            }),
        NEGATE_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "negate_int64",
                    Long.class,
                    (Long x) -> {
                      try {
                        return RuntimeHelpers.int64Negate(x, bindingHelper.celOptions);
                      } catch (ArithmeticException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            getArithmeticErrorCode(e));
                      }
                    })),
        NEGATE_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from("negate_double", Double.class, (Double x) -> -x));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        Arithmetic(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for indexing a list or a map. */
      public enum Index implements StandardOverload {
        INDEX_LIST(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "index_list", List.class, Number.class, RuntimeHelpers::indexList)),
        INDEX_MAP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "index_map",
                    Map.class,
                    Object.class,
                    (Map map, Object key) ->
                        bindingHelper.runtimeEquality.indexMap(
                            map, key, bindingHelper.celOptions)));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        Index(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for retrieving the size of a literal or a collection. */
      public enum Size implements StandardOverload {
        SIZE_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "size_bytes", ByteString.class, (ByteString bytes) -> (long) bytes.size())),
        BYTES_SIZE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "bytes_size", ByteString.class, (ByteString bytes) -> (long) bytes.size())),
        SIZE_LIST(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "size_list", List.class, (List list1) -> (long) list1.size())),
        LIST_SIZE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "list_size", List.class, (List list1) -> (long) list1.size())),
        SIZE_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "size_string",
                    String.class,
                    (String s) -> (long) s.codePointCount(0, s.length()))),
        STRING_SIZE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "string_size",
                    String.class,
                    (String s) -> (long) s.codePointCount(0, s.length()))),
        SIZE_MAP(
            (bindingHelper) ->
                CelFunctionBinding.from("size_map", Map.class, (Map map1) -> (long) map1.size())),
        MAP_SIZE(
            (bindingHelper) ->
                CelFunctionBinding.from("map_size", Map.class, (Map map1) -> (long) map1.size()));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        Size(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for performing type conversions. */
      public enum Conversions implements StandardOverload {
        // Bool conversions
        BOOL_TO_BOOL(
            (bindingHelper) ->
                CelFunctionBinding.from("bool_to_bool", Boolean.class, (Boolean x) -> x)),
        STRING_TO_BOOL(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "string_to_bool",
                    String.class,
                    (String str) -> {
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
                          throw new CelEvaluationException(
                              new InterpreterException.Builder(
                                      "Type conversion error from 'string' to 'bool': [%s]", str)
                                  .build(),
                              CelErrorCode.BAD_FORMAT);
                      }
                    })),
        // Int conversions
        INT64_TO_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from("int64_to_int64", Long.class, (Long x) -> x)),
        DOUBLE_TO_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "double_to_int64",
                    Double.class,
                    (Double arg) -> {
                      if (bindingHelper.celOptions.errorOnIntWrap()) {
                        return RuntimeHelpers.doubleToLongChecked(arg)
                            .orElseThrow(
                                () ->
                                    new CelEvaluationException(
                                        new InterpreterException.Builder(
                                                "double is out of range for int")
                                            .build(),
                                        CelErrorCode.NUMERIC_OVERFLOW));
                      }
                      return arg.longValue();
                    })),
        STRING_TO_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "string_to_int64",
                    String.class,
                    (String arg) -> {
                      try {
                        return Long.parseLong(arg);
                      } catch (NumberFormatException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            CelErrorCode.BAD_FORMAT);
                      }
                    })),
        TIMESTAMP_TO_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_int64", Timestamp.class, Timestamps::toSeconds)),
        UINT64_TO_INT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "uint64_to_int64",
                    UnsignedLong.class,
                    (UnsignedLong arg) -> {
                      if (arg.compareTo(UnsignedLong.valueOf(Long.MAX_VALUE)) > 0) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder("unsigned out of int range").build(),
                            CelErrorCode.NUMERIC_OVERFLOW);
                      }
                      return arg.longValue();
                    });
              } else {
                return CelFunctionBinding.from(
                    "uint64_to_int64",
                    Long.class,
                    (Long arg) -> {
                      if (bindingHelper.celOptions.errorOnIntWrap() && arg < 0) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder("unsigned out of int range").build(),
                            CelErrorCode.NUMERIC_OVERFLOW);
                      }
                      return arg;
                    });
              }
            }),
        // Uint conversions
        UINT64_TO_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "uint64_to_uint64", UnsignedLong.class, (UnsignedLong x) -> x);
              } else {
                return CelFunctionBinding.from("uint64_to_uint64", Long.class, (Long x) -> x);
              }
            }),
        INT64_TO_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "int64_to_uint64",
                    Long.class,
                    (Long arg) -> {
                      if (bindingHelper.celOptions.errorOnIntWrap() && arg < 0) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder("int out of uint range").build(),
                            CelErrorCode.NUMERIC_OVERFLOW);
                      }
                      return UnsignedLong.valueOf(arg);
                    });
              } else {
                return CelFunctionBinding.from(
                    "int64_to_uint64",
                    Long.class,
                    (Long arg) -> {
                      if (bindingHelper.celOptions.errorOnIntWrap() && arg < 0) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder("int out of uint range").build(),
                            CelErrorCode.NUMERIC_OVERFLOW);
                      }
                      return arg;
                    });
              }
            }),
        DOUBLE_TO_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "double_to_uint64",
                    Double.class,
                    (Double arg) -> {
                      if (bindingHelper.celOptions.errorOnIntWrap()) {
                        return RuntimeHelpers.doubleToUnsignedChecked(arg)
                            .orElseThrow(
                                () ->
                                    new CelEvaluationException(
                                        new InterpreterException.Builder("double out of uint range")
                                            .build(),
                                        CelErrorCode.NUMERIC_OVERFLOW));
                      }
                      return UnsignedLong.valueOf(BigDecimal.valueOf(arg).toBigInteger());
                    });
              } else {
                return CelFunctionBinding.from(
                    "double_to_uint64",
                    Double.class,
                    (Double arg) -> {
                      if (bindingHelper.celOptions.errorOnIntWrap()) {
                        return RuntimeHelpers.doubleToUnsignedChecked(arg)
                            .map(UnsignedLong::longValue)
                            .orElseThrow(
                                () ->
                                    new CelEvaluationException(
                                        new InterpreterException.Builder("double out of uint range")
                                            .build(),
                                        CelErrorCode.NUMERIC_OVERFLOW));
                      }
                      return arg.longValue();
                    });
              }
            }),
        STRING_TO_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "string_to_uint64",
                    String.class,
                    (String arg) -> {
                      try {
                        return UnsignedLong.valueOf(arg);
                      } catch (NumberFormatException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            CelErrorCode.BAD_FORMAT);
                      }
                    });
              } else {
                return CelFunctionBinding.from(
                    "string_to_uint64",
                    String.class,
                    (String arg) -> {
                      try {
                        return UnsignedLongs.parseUnsignedLong(arg);
                      } catch (NumberFormatException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            CelErrorCode.BAD_FORMAT);
                      }
                    });
              }
            }),
        // Double conversions
        DOUBLE_TO_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from("double_to_double", Double.class, (Double x) -> x)),
        INT64_TO_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from("int64_to_double", Long.class, Long::doubleValue)),
        STRING_TO_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "string_to_double",
                    String.class,
                    (String arg) -> {
                      try {
                        return Double.parseDouble(arg);
                      } catch (NumberFormatException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            CelErrorCode.BAD_FORMAT);
                      }
                    })),
        UINT64_TO_DOUBLE(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "uint64_to_double", UnsignedLong.class, UnsignedLong::doubleValue);
              } else {
                return CelFunctionBinding.from(
                    "uint64_to_double",
                    Long.class,
                    (Long arg) -> UnsignedLong.fromLongBits(arg).doubleValue());
              }
            }),
        // String conversions
        STRING_TO_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from("string_to_string", String.class, (String x) -> x)),
        INT64_TO_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from("int64_to_string", Long.class, Object::toString)),
        DOUBLE_TO_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from("double_to_string", Double.class, Object::toString)),
        BYTES_TO_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "bytes_to_string", ByteString.class, ByteString::toStringUtf8)),
        TIMESTAMP_TO_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_string", Timestamp.class, Timestamps::toString)),
        DURATION_TO_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from("duration_to_string", Duration.class, Durations::toString)),
        UINT64_TO_STRING(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "uint64_to_string", UnsignedLong.class, UnsignedLong::toString);
              } else {
                return CelFunctionBinding.from(
                    "uint64_to_string", Long.class, UnsignedLongs::toString);
              }
            }),

        // Bytes conversions
        BYTES_TO_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from("bytes_to_bytes", ByteString.class, (ByteString x) -> x)),
        STRING_TO_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from("string_to_bytes", String.class, ByteString::copyFromUtf8)),
        // Duration conversions
        DURATION_TO_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from("duration_to_duration", Duration.class, (Duration x) -> x)),
        STRING_TO_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "string_to_duration",
                    String.class,
                    (String d) -> {
                      try {
                        return RuntimeHelpers.createDurationFromString(d);
                      } catch (IllegalArgumentException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).build(),
                            CelErrorCode.BAD_FORMAT);
                      }
                    })),

        STRING_TO_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "string_to_timestamp",
                    String.class,
                    (String ts) -> {
                      try {
                        return Timestamps.parse(ts);
                      } catch (ParseException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).build(),
                            CelErrorCode.BAD_FORMAT);
                      }
                    })),
        TIMESTAMP_TO_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_timestamp", Timestamp.class, (Timestamp x) -> x)),
        INT64_TO_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from("int64_to_timestamp", Long.class, Timestamps::fromSeconds)),
        TO_DYN(
            (bindingHelper) ->
                CelFunctionBinding.from("to_dyn", Object.class, (Object arg) -> arg));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        Conversions(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /**
       * Overloads for functions performing string matching, such as regular expressions or contains
       * check.
       */
      public enum StringMatchers implements StandardOverload {
        MATCHES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "matches",
                    String.class,
                    String.class,
                    (String string, String regexp) -> {
                      try {
                        return RuntimeHelpers.matches(string, regexp, bindingHelper.celOptions);
                      } catch (PatternSyntaxException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            CelErrorCode.INVALID_ARGUMENT);
                      }
                    })),
        // Duplicate receiver-style matches overload.
        MATCHES_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "matches_string",
                    String.class,
                    String.class,
                    (String string, String regexp) -> {
                      try {
                        return RuntimeHelpers.matches(string, regexp, bindingHelper.celOptions);
                      } catch (PatternSyntaxException e) {
                        throw new CelEvaluationException(
                            new InterpreterException.Builder(e.getMessage()).setCause(e).build(),
                            CelErrorCode.INVALID_ARGUMENT);
                      }
                    })),
        CONTAINS_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "contains_string", String.class, String.class, String::contains)),
        ENDS_WITH_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "ends_with_string", String.class, String.class, String::endsWith)),
        STARTS_WITH_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "starts_with_string", String.class, String.class, String::startsWith));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        StringMatchers(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for logical operators that return a bool as a result. */
      public enum BooleanOperator implements StandardOverload {
        LOGICAL_NOT(
            (bindingHelper) ->
                CelFunctionBinding.from("logical_not", Boolean.class, (Boolean x) -> !x));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        BooleanOperator(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for functions performing date/time operations. */
      public enum DateTime implements StandardOverload {
        TIMESTAMP_TO_YEAR(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_year",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getYear())),
        TIMESTAMP_TO_YEAR_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_year_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getYear())),
        TIMESTAMP_TO_MONTH(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_month",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getMonthValue() - 1)),
        TIMESTAMP_TO_MONTH_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_month_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) ->
                        (long) newLocalDateTime(ts, tz).getMonthValue() - 1)),
        TIMESTAMP_TO_DAY_OF_YEAR(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_year",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfYear() - 1)),
        TIMESTAMP_TO_DAY_OF_YEAR_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_year_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) ->
                        (long) newLocalDateTime(ts, tz).getDayOfYear() - 1)),
        TIMESTAMP_TO_DAY_OF_MONTH(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_month",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth() - 1)),
        TIMESTAMP_TO_DAY_OF_MONTH_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_month_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) ->
                        (long) newLocalDateTime(ts, tz).getDayOfMonth() - 1)),

        TIMESTAMP_TO_DAY_OF_MONTH_1_BASED(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_month_1_based",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getDayOfMonth())),
        TIMESTAMP_TO_DAY_OF_MONTH_1_BASED_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_month_1_based_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getDayOfMonth())),

        TIMESTAMP_TO_DAY_OF_WEEK(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_week",
                    Timestamp.class,
                    (Timestamp ts) -> {
                      // CEL treats Sunday as day 0, but Java.time treats it as day 7.
                      DayOfWeek dayOfWeek = newLocalDateTime(ts, UTC).getDayOfWeek();
                      return (long) dayOfWeek.getValue() % 7;
                    })),
        TIMESTAMP_TO_DAY_OF_WEEK_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_day_of_week_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) -> {
                      // CEL treats Sunday as day 0, but Java.time treats it as day 7.
                      DayOfWeek dayOfWeek = newLocalDateTime(ts, tz).getDayOfWeek();
                      return (long) dayOfWeek.getValue() % 7;
                    })),
        TIMESTAMP_TO_HOURS(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_hours",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getHour())),
        TIMESTAMP_TO_HOURS_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_hours_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getHour())),
        TIMESTAMP_TO_MINUTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_minutes",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getMinute())),
        TIMESTAMP_TO_MINUTES_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_minutes_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getMinute())),
        TIMESTAMP_TO_SECONDS(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_seconds",
                    Timestamp.class,
                    (Timestamp ts) -> (long) newLocalDateTime(ts, UTC).getSecond())),
        TIMESTAMP_TO_SECONDS_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_seconds_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) -> (long) newLocalDateTime(ts, tz).getSecond())),
        // We specifically need to only access nanos-of-second field for
        // timestamp_to_milliseconds overload
        @SuppressWarnings("JavaLocalDateTimeGetNano")
        TIMESTAMP_TO_MILLISECONDS(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_milliseconds",
                    Timestamp.class,
                    (Timestamp ts) -> (long) (newLocalDateTime(ts, UTC).getNano() / 1e+6))),

        @SuppressWarnings("JavaLocalDateTimeGetNano")
        TIMESTAMP_TO_MILLISECONDS_WITH_TZ(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "timestamp_to_milliseconds_with_tz",
                    Timestamp.class,
                    String.class,
                    (Timestamp ts, String tz) ->
                        (long) (newLocalDateTime(ts, tz).getNano() / 1e+6))),
        DURATION_TO_HOURS(
            (bindingHelper) ->
                CelFunctionBinding.from("duration_to_hours", Duration.class, Durations::toHours)),
        DURATION_TO_MINUTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "duration_to_minutes", Duration.class, Durations::toMinutes)),
        DURATION_TO_SECONDS(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "duration_to_seconds", Duration.class, Durations::toSeconds)),
        DURATION_TO_MILLISECONDS(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "duration_to_milliseconds",
                    Duration.class,
                    (Duration arg) ->
                        Durations.toMillis(arg) % java.time.Duration.ofSeconds(1).toMillis()));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        DateTime(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for performing numeric comparisons. */
      public enum Comparison implements StandardOverload {
        LESS_BOOL(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_bool", Boolean.class, Boolean.class, (Boolean x, Boolean y) -> !x && y),
            false),
        LESS_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_int64", Long.class, Long.class, (Long x, Long y) -> x < y),
            false),
        LESS_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "less_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) < 0);
              } else {
                return CelFunctionBinding.from(
                    "less_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) ->
                        RuntimeHelpers.uint64CompareTo(x, y, bindingHelper.celOptions) < 0);
              }
            },
            false),
        LESS_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_bytes",
                    ByteString.class,
                    ByteString.class,
                    (ByteString x, ByteString y) ->
                        ByteString.unsignedLexicographicalComparator().compare(x, y) < 0),
            false),
        LESS_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_double", Double.class, Double.class, (Double x, Double y) -> x < y),
            false),
        LESS_DOUBLE_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_double_uint64",
                    Double.class,
                    UnsignedLong.class,
                    (Double x, UnsignedLong y) ->
                        ComparisonFunctions.compareDoubleUint(x, y) == -1),
            true),
        LESS_INT64_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_int64_uint64",
                    Long.class,
                    UnsignedLong.class,
                    (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) == -1),
            true),
        LESS_UINT64_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_uint64_int64",
                    UnsignedLong.class,
                    Long.class,
                    (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) == -1),
            true),
        LESS_INT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_int64_double",
                    Long.class,
                    Double.class,
                    (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) == -1),
            true),
        LESS_DOUBLE_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_double_int64",
                    Double.class,
                    Long.class,
                    (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) == -1),
            true),
        LESS_UINT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_uint64_double",
                    UnsignedLong.class,
                    Double.class,
                    (UnsignedLong x, Double y) ->
                        ComparisonFunctions.compareUintDouble(x, y) == -1),
            true),
        LESS_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_duration",
                    Duration.class,
                    Duration.class,
                    (Duration x, Duration y) -> Durations.compare(x, y) < 0),
            false),
        LESS_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_string",
                    String.class,
                    String.class,
                    (String x, String y) -> x.compareTo(y) < 0),
            false),
        LESS_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_timestamp",
                    Timestamp.class,
                    Timestamp.class,
                    (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) < 0),
            false),
        LESS_EQUALS_BOOL(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_bool",
                    Boolean.class,
                    Boolean.class,
                    (Boolean x, Boolean y) -> !x || y),
            false),
        LESS_EQUALS_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_bytes",
                    ByteString.class,
                    ByteString.class,
                    (ByteString x, ByteString y) ->
                        ByteString.unsignedLexicographicalComparator().compare(x, y) <= 0),
            false),
        LESS_EQUALS_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_double",
                    Double.class,
                    Double.class,
                    (Double x, Double y) -> x <= y),
            false),
        LESS_EQUALS_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_duration",
                    Duration.class,
                    Duration.class,
                    (Duration x, Duration y) -> Durations.compare(x, y) <= 0),
            false),
        LESS_EQUALS_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_int64", Long.class, Long.class, (Long x, Long y) -> x <= y),
            false),
        LESS_EQUALS_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_string",
                    String.class,
                    String.class,
                    (String x, String y) -> x.compareTo(y) <= 0),
            false),
        LESS_EQUALS_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_timestamp",
                    Timestamp.class,
                    Timestamp.class,
                    (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) <= 0),
            false),
        LESS_EQUALS_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "less_equals_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) <= 0);
              } else {
                return CelFunctionBinding.from(
                    "less_equals_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) ->
                        RuntimeHelpers.uint64CompareTo(x, y, bindingHelper.celOptions) <= 0);
              }
            },
            false),
        LESS_EQUALS_INT64_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_int64_uint64",
                    Long.class,
                    UnsignedLong.class,
                    (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) <= 0),
            true),
        LESS_EQUALS_UINT64_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_uint64_int64",
                    UnsignedLong.class,
                    Long.class,
                    (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) <= 0),
            true),
        LESS_EQUALS_INT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_int64_double",
                    Long.class,
                    Double.class,
                    (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) <= 0),
            true),
        LESS_EQUALS_DOUBLE_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_double_int64",
                    Double.class,
                    Long.class,
                    (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) <= 0),
            true),
        LESS_EQUALS_UINT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_uint64_double",
                    UnsignedLong.class,
                    Double.class,
                    (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) <= 0),
            true),
        LESS_EQUALS_DOUBLE_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "less_equals_double_uint64",
                    Double.class,
                    UnsignedLong.class,
                    (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) <= 0),
            true),
        GREATER_BOOL(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_bool",
                    Boolean.class,
                    Boolean.class,
                    (Boolean x, Boolean y) -> x && !y),
            false),
        GREATER_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_bytes",
                    ByteString.class,
                    ByteString.class,
                    (ByteString x, ByteString y) ->
                        ByteString.unsignedLexicographicalComparator().compare(x, y) > 0),
            false),
        GREATER_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_double", Double.class, Double.class, (Double x, Double y) -> x > y),
            false),
        GREATER_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_duration",
                    Duration.class,
                    Duration.class,
                    (Duration x, Duration y) -> Durations.compare(x, y) > 0),
            false),
        GREATER_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_int64", Long.class, Long.class, (Long x, Long y) -> x > y),
            false),
        GREATER_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_string",
                    String.class,
                    String.class,
                    (String x, String y) -> x.compareTo(y) > 0),
            false),
        GREATER_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_timestamp",
                    Timestamp.class,
                    Timestamp.class,
                    (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) > 0),
            false),
        GREATER_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "greater_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) > 0);
              } else {
                return CelFunctionBinding.from(
                    "greater_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) ->
                        RuntimeHelpers.uint64CompareTo(x, y, bindingHelper.celOptions) > 0);
              }
            },
            false),
        GREATER_INT64_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_int64_uint64",
                    Long.class,
                    UnsignedLong.class,
                    (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) == 1),
            true),
        GREATER_UINT64_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_uint64_int64",
                    UnsignedLong.class,
                    Long.class,
                    (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) == 1),
            true),
        GREATER_INT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_int64_double",
                    Long.class,
                    Double.class,
                    (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) == 1),
            true),
        GREATER_DOUBLE_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_double_int64",
                    Double.class,
                    Long.class,
                    (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) == 1),
            true),
        GREATER_UINT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_uint64_double",
                    UnsignedLong.class,
                    Double.class,
                    (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) == 1),
            true),
        GREATER_DOUBLE_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_double_uint64",
                    Double.class,
                    UnsignedLong.class,
                    (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) == 1),
            true),
        GREATER_EQUALS_BOOL(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_bool",
                    Boolean.class,
                    Boolean.class,
                    (Boolean x, Boolean y) -> x || !y),
            false),
        GREATER_EQUALS_BYTES(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_bytes",
                    ByteString.class,
                    ByteString.class,
                    (ByteString x, ByteString y) ->
                        ByteString.unsignedLexicographicalComparator().compare(x, y) >= 0),
            false),
        GREATER_EQUALS_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_double",
                    Double.class,
                    Double.class,
                    (Double x, Double y) -> x >= y),
            false),
        GREATER_EQUALS_DURATION(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_duration",
                    Duration.class,
                    Duration.class,
                    (Duration x, Duration y) -> Durations.compare(x, y) >= 0),
            false),
        GREATER_EQUALS_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_int64", Long.class, Long.class, (Long x, Long y) -> x >= y),
            false),
        GREATER_EQUALS_STRING(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_string",
                    String.class,
                    String.class,
                    (String x, String y) -> x.compareTo(y) >= 0),
            false),
        GREATER_EQUALS_TIMESTAMP(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_timestamp",
                    Timestamp.class,
                    Timestamp.class,
                    (Timestamp x, Timestamp y) -> Timestamps.compare(x, y) >= 0),
            false),
        GREATER_EQUALS_UINT64(
            (bindingHelper) -> {
              if (bindingHelper.celOptions.enableUnsignedLongs()) {
                return CelFunctionBinding.from(
                    "greater_equals_uint64",
                    UnsignedLong.class,
                    UnsignedLong.class,
                    (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) >= 0);
              } else {
                return CelFunctionBinding.from(
                    "greater_equals_uint64",
                    Long.class,
                    Long.class,
                    (Long x, Long y) ->
                        RuntimeHelpers.uint64CompareTo(x, y, bindingHelper.celOptions) >= 0);
              }
            },
            false),
        GREATER_EQUALS_INT64_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_int64_uint64",
                    Long.class,
                    UnsignedLong.class,
                    (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) >= 0),
            true),
        GREATER_EQUALS_UINT64_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_uint64_int64",
                    UnsignedLong.class,
                    Long.class,
                    (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) >= 0),
            true),
        GREATER_EQUALS_INT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_int64_double",
                    Long.class,
                    Double.class,
                    (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) >= 0),
            true),
        GREATER_EQUALS_DOUBLE_INT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_double_int64",
                    Double.class,
                    Long.class,
                    (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) >= 0),
            true),
        GREATER_EQUALS_UINT64_DOUBLE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_uint64_double",
                    UnsignedLong.class,
                    Double.class,
                    (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) >= 0),
            true),
        GREATER_EQUALS_DOUBLE_UINT64(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "greater_equals_double_uint64",
                    Double.class,
                    UnsignedLong.class,
                    (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) >= 0),
            true);

        private final FunctionBindingCreator bindingCreator;
        private final boolean isHeterogeneousComparison;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        Comparison(FunctionBindingCreator bindingCreator, boolean isHeterogeneousComparison) {
          this.bindingCreator = bindingCreator;
          this.isHeterogeneousComparison = isHeterogeneousComparison;
        }

        public boolean isHeterogeneousComparison() {
          return isHeterogeneousComparison;
        }
      }

      /** Overloads for optional values. */
      public enum OptionalValue implements StandardOverload {
        SELECT_OPTIONAL_FIELD(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "select_optional_field", // This only handles map selection. Proto selection is
                    // special cased inside the interpreter.
                    Map.class,
                    String.class,
                    (Map map, String key) ->
                        bindingHelper.runtimeEquality.findInMap(
                            map, key, bindingHelper.celOptions))),
        MAP_OPTINDEX_OPTIONAL_VALUE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "map_optindex_optional_value",
                    Map.class,
                    Object.class,
                    (Map map, Object key) ->
                        bindingHelper.runtimeEquality.findInMap(
                            map, key, bindingHelper.celOptions))),
        OPTIONAL_MAP_OPTINDEX_OPTIONAL_VALUE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "optional_map_optindex_optional_value",
                    Optional.class,
                    Object.class,
                    (Optional optionalMap, Object key) ->
                        indexOptionalMap(
                            optionalMap,
                            key,
                            bindingHelper.celOptions,
                            bindingHelper.runtimeEquality))),
        OPTIONAL_MAP_INDEX_VALUE(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "optional_map_index_value",
                    Optional.class,
                    Object.class,
                    (Optional optionalMap, Object key) ->
                        indexOptionalMap(
                            optionalMap,
                            key,
                            bindingHelper.celOptions,
                            bindingHelper.runtimeEquality))),
        OPTIONAL_LIST_INDEX_INT(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "optional_list_index_int",
                    Optional.class,
                    Long.class,
                    CelStandardFunctions::indexOptionalList)),
        LIST_OPTINDEX_OPTIONAL_INT(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "list_optindex_optional_int",
                    List.class,
                    Long.class,
                    (List list, Long index) -> {
                      int castIndex = Ints.checkedCast(index);
                      if (castIndex < 0 || castIndex >= list.size()) {
                        return Optional.empty();
                      }
                      return Optional.of(list.get(castIndex));
                    })),
        OPTIONAL_LIST_OPTINDEX_OPTIONAL_INT(
            (bindingHelper) ->
                CelFunctionBinding.from(
                    "optional_list_optindex_optional_int",
                    Optional.class,
                    Long.class,
                    CelStandardFunctions::indexOptionalList));

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper) {
          return bindingCreator.create(functionBindingHelper);
        }

        OptionalValue(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      private Overload() {}
    }

    private final ImmutableSet<StandardOverload> standardOverloads;

    StandardFunction(StandardOverload... overloads) {
      this.standardOverloads = ImmutableSet.copyOf(overloads);
    }

    @VisibleForTesting
    ImmutableSet<StandardOverload> getOverloads() {
      return standardOverloads;
    }
  }

  @VisibleForTesting
  ImmutableSet<StandardOverload> getOverloads() {
    return standardOverloads;
  }

  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      DynamicProto dynamicProto, CelOptions celOptions) {
    FunctionBindingHelper helper = new FunctionBindingHelper(celOptions, dynamicProto);
    ImmutableSet.Builder<CelFunctionBinding> builder = ImmutableSet.builder();
    for (StandardOverload overload : standardOverloads) {
      builder.add(overload.newFunctionBinding(helper));
    }

    return builder.build();
  }

  /** General interface for defining a standard function overload. */
  @Immutable
  public interface StandardOverload {
    CelFunctionBinding newFunctionBinding(FunctionBindingHelper functionBindingHelper);
  }

  /** Builder for constructing the set of standard function/identifiers. */
  public static final class Builder {
    private ImmutableSet<StandardFunction> includeFunctions;
    private ImmutableSet<StandardFunction> excludeFunctions;

    private FunctionFilter functionFilter;

    private Builder() {
      this.includeFunctions = ImmutableSet.of();
      this.excludeFunctions = ImmutableSet.of();
    }

    @CanIgnoreReturnValue
    public Builder excludeFunctions(StandardFunction... functions) {
      return excludeFunctions(ImmutableSet.copyOf(functions));
    }

    @CanIgnoreReturnValue
    public Builder excludeFunctions(Iterable<StandardFunction> functions) {
      this.excludeFunctions = checkNotNull(ImmutableSet.copyOf(functions));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder includeFunctions(StandardFunction... functions) {
      return includeFunctions(ImmutableSet.copyOf(functions));
    }

    @CanIgnoreReturnValue
    public Builder includeFunctions(Iterable<StandardFunction> functions) {
      this.includeFunctions = checkNotNull(ImmutableSet.copyOf(functions));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder filterFunctions(FunctionFilter functionFilter) {
      this.functionFilter = functionFilter;
      return this;
    }

    private static void assertOneSettingIsSet(
        boolean a, boolean b, boolean c, String errorMessage) {
      int count = 0;
      if (a) {
        count++;
      }
      if (b) {
        count++;
      }
      if (c) {
        count++;
      }

      if (count > 1) {
        throw new IllegalArgumentException(errorMessage);
      }
    }

    public CelStandardFunctions build() {
      boolean hasIncludeFunctions = !this.includeFunctions.isEmpty();
      boolean hasExcludeFunctions = !this.excludeFunctions.isEmpty();
      boolean hasFilterFunction = this.functionFilter != null;
      assertOneSettingIsSet(
          hasIncludeFunctions,
          hasExcludeFunctions,
          hasFilterFunction,
          "You may only populate one of the following builder methods: includeFunctions,"
              + " excludeFunctions or filterFunctions");

      ImmutableSet.Builder<StandardOverload> standardOverloadBuilder = ImmutableSet.builder();
      for (StandardFunction standardFunction : StandardFunction.values()) {
        if (hasIncludeFunctions) {
          if (this.includeFunctions.contains(standardFunction)) {
            standardOverloadBuilder.addAll(standardFunction.standardOverloads);
          }
          continue;
        }
        if (hasExcludeFunctions) {
          if (!this.excludeFunctions.contains(standardFunction)) {
            standardOverloadBuilder.addAll(standardFunction.standardOverloads);
          }
          continue;
        }
        if (hasFilterFunction) {
          ImmutableSet.Builder<StandardOverload> filteredOverloadsBuilder = ImmutableSet.builder();
          for (StandardOverload standardOverload : standardFunction.standardOverloads) {
            boolean includeOverload = functionFilter.include(standardFunction, standardOverload);
            if (includeOverload) {
              standardOverloadBuilder.add(standardOverload);
            }
          }

          ImmutableSet<StandardOverload> filteredOverloads = filteredOverloadsBuilder.build();
          if (!filteredOverloads.isEmpty()) {
            standardOverloadBuilder.addAll(filteredOverloads);
          }

          continue;
        }

        standardOverloadBuilder.addAll(standardFunction.standardOverloads);
      }

      return new CelStandardFunctions(standardOverloadBuilder.build());
    }

    /**
     * Functional interface for filtering standard functions. Returning true in the callback will
     * include the function in the environment.
     */
    @FunctionalInterface
    public interface FunctionFilter {
      boolean include(StandardFunction standardFunction, StandardOverload standardOverload);
    }
  }

  /** Creates a new builder to configure CelStandardFunctions. */
  public static Builder newBuilder() {
    return new Builder();
  }

  @Immutable
  private static final class FunctionBindingHelper {
    private final CelOptions celOptions;
    private final RuntimeEquality runtimeEquality;

    private FunctionBindingHelper(CelOptions celOptions, DynamicProto dynamicProto) {
      this.celOptions = celOptions;
      this.runtimeEquality = new RuntimeEquality(dynamicProto);
    }
  }

  @FunctionalInterface
  @Immutable
  private interface FunctionBindingCreator {
    CelFunctionBinding create(FunctionBindingHelper helper);
  }

  private static CelErrorCode getArithmeticErrorCode(ArithmeticException e) {
    String exceptionMessage = e.getMessage();
    // The two known cases for an arithmetic exception is divide by zero and overflow.
    if (exceptionMessage.equals("/ by zero")) {
      return CelErrorCode.DIVIDE_BY_ZERO;
    }
    return CelErrorCode.NUMERIC_OVERFLOW;
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
      throws CelEvaluationException {
    return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())
        .atZone(timeZone(tz))
        .toLocalDateTime();
  }

  /**
   * Get the DateTimeZone Instance.
   *
   * @param tz the ID of the datetime zone
   * @return the ZoneId object
   * @throws CelEvaluationException if there is an invalid timezone
   */
  private static ZoneId timeZone(String tz) throws CelEvaluationException {
    try {
      return ZoneId.of(tz);
    } catch (DateTimeException e) {
      // If timezone is not a string name (for example, 'US/Central'), it should be a numerical
      // offset from UTC in the format [+/-]HH:MM.
      try {
        int ind = tz.indexOf(":");
        if (ind == -1) {
          throw new CelEvaluationException(
              new InterpreterException.Builder(e.getMessage()).build());
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
        throw new CelEvaluationException(new InterpreterException.Builder(e2.getMessage()).build());
      }
    }
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

  private CelStandardFunctions(ImmutableSet<StandardOverload> standardOverloads) {
    this.standardOverloads = standardOverloads;
  }
}
