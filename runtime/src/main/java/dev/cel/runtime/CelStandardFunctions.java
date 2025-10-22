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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
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
import dev.cel.runtime.standard.AddOperator;
import dev.cel.runtime.standard.AddOperator.AddOverload;
import dev.cel.runtime.standard.BoolFunction;
import dev.cel.runtime.standard.BoolFunction.BoolOverload;
import dev.cel.runtime.standard.BytesFunction;
import dev.cel.runtime.standard.BytesFunction.BytesOverload;
import dev.cel.runtime.standard.CelStandardFunction;
import dev.cel.runtime.standard.ContainsFunction;
import dev.cel.runtime.standard.ContainsFunction.ContainsOverload;
import dev.cel.runtime.standard.DivideOperator;
import dev.cel.runtime.standard.DivideOperator.DivideOverload;
import dev.cel.runtime.standard.DoubleFunction;
import dev.cel.runtime.standard.DoubleFunction.DoubleOverload;
import dev.cel.runtime.standard.DurationFunction;
import dev.cel.runtime.standard.DurationFunction.DurationOverload;
import dev.cel.runtime.standard.DynFunction;
import dev.cel.runtime.standard.DynFunction.DynOverload;
import dev.cel.runtime.standard.EndsWithFunction;
import dev.cel.runtime.standard.EndsWithFunction.EndsWithOverload;
import dev.cel.runtime.standard.EqualsOperator;
import dev.cel.runtime.standard.EqualsOperator.EqualsOverload;
import dev.cel.runtime.standard.GetDateFunction;
import dev.cel.runtime.standard.GetDateFunction.GetDateOverload;
import dev.cel.runtime.standard.GetDayOfMonthFunction;
import dev.cel.runtime.standard.GetDayOfMonthFunction.GetDayOfMonthOverload;
import dev.cel.runtime.standard.GetDayOfWeekFunction;
import dev.cel.runtime.standard.GetDayOfWeekFunction.GetDayOfWeekOverload;
import dev.cel.runtime.standard.GetDayOfYearFunction;
import dev.cel.runtime.standard.GetDayOfYearFunction.GetDayOfYearOverload;
import dev.cel.runtime.standard.GetFullYearFunction;
import dev.cel.runtime.standard.GetFullYearFunction.GetFullYearOverload;
import dev.cel.runtime.standard.GetHoursFunction;
import dev.cel.runtime.standard.GetHoursFunction.GetHoursOverload;
import dev.cel.runtime.standard.GetMillisecondsFunction;
import dev.cel.runtime.standard.GetMillisecondsFunction.GetMillisecondsOverload;
import dev.cel.runtime.standard.GetMinutesFunction;
import dev.cel.runtime.standard.GetMinutesFunction.GetMinutesOverload;
import dev.cel.runtime.standard.GetMonthFunction;
import dev.cel.runtime.standard.GetMonthFunction.GetMonthOverload;
import dev.cel.runtime.standard.GetSecondsFunction;
import dev.cel.runtime.standard.GetSecondsFunction.GetSecondsOverload;
import dev.cel.runtime.standard.GreaterEqualsOperator;
import dev.cel.runtime.standard.GreaterEqualsOperator.GreaterEqualsOverload;
import dev.cel.runtime.standard.GreaterOperator;
import dev.cel.runtime.standard.GreaterOperator.GreaterOverload;
import dev.cel.runtime.standard.InOperator;
import dev.cel.runtime.standard.InOperator.InOverload;
import dev.cel.runtime.standard.IndexOperator;
import dev.cel.runtime.standard.IndexOperator.IndexOverload;
import dev.cel.runtime.standard.IntFunction;
import dev.cel.runtime.standard.IntFunction.IntOverload;
import dev.cel.runtime.standard.LessEqualsOperator;
import dev.cel.runtime.standard.LessEqualsOperator.LessEqualsOverload;
import dev.cel.runtime.standard.LessOperator;
import dev.cel.runtime.standard.LessOperator.LessOverload;
import dev.cel.runtime.standard.LogicalNotOperator;
import dev.cel.runtime.standard.LogicalNotOperator.LogicalNotOverload;
import dev.cel.runtime.standard.MatchesFunction;
import dev.cel.runtime.standard.MatchesFunction.MatchesOverload;
import dev.cel.runtime.standard.ModuloOperator;
import dev.cel.runtime.standard.ModuloOperator.ModuloOverload;
import dev.cel.runtime.standard.MultiplyOperator;
import dev.cel.runtime.standard.MultiplyOperator.MultiplyOverload;
import dev.cel.runtime.standard.NegateOperator;
import dev.cel.runtime.standard.NegateOperator.NegateOverload;
import dev.cel.runtime.standard.NotEqualsOperator;
import dev.cel.runtime.standard.NotEqualsOperator.NotEqualsOverload;
import dev.cel.runtime.standard.SizeFunction;
import dev.cel.runtime.standard.SizeFunction.SizeOverload;
import dev.cel.runtime.standard.StartsWithFunction;
import dev.cel.runtime.standard.StartsWithFunction.StartsWithOverload;
import dev.cel.runtime.standard.StringFunction;
import dev.cel.runtime.standard.StringFunction.StringOverload;
import dev.cel.runtime.standard.SubtractOperator;
import dev.cel.runtime.standard.SubtractOperator.SubtractOverload;
import dev.cel.runtime.standard.TimestampFunction;
import dev.cel.runtime.standard.TimestampFunction.TimestampOverload;
import dev.cel.runtime.standard.UintFunction;
import dev.cel.runtime.standard.UintFunction.UintOverload;

/** Runtime function bindings for the standard functions in CEL. */
@Immutable
public final class CelStandardFunctions {

  private final ImmutableSet<StandardOverload> standardOverloads;

  public static final ImmutableSet<CelStandardFunction> ALL_STANDARD_FUNCTIONS =
      ImmutableSet.of(
          AddOperator.create(),
          BoolFunction.create(),
          BytesFunction.create(),
          ContainsFunction.create(),
          DivideOperator.create(),
          DoubleFunction.create(),
          DurationFunction.create(),
          DynFunction.create(),
          EndsWithFunction.create(),
          EqualsOperator.create(),
          GetDateFunction.create(),
          GetDayOfMonthFunction.create(),
          GetDayOfWeekFunction.create(),
          GetDayOfYearFunction.create(),
          GetFullYearFunction.create(),
          GetHoursFunction.create(),
          GetMillisecondsFunction.create(),
          GetMinutesFunction.create(),
          GetMonthFunction.create(),
          GetSecondsFunction.create(),
          GreaterEqualsOperator.create(),
          GreaterOperator.create(),
          IndexOperator.create(),
          InOperator.create(),
          IntFunction.create(),
          LessEqualsOperator.create(),
          LessOperator.create(),
          LogicalNotOperator.create(),
          MatchesFunction.create(),
          ModuloOperator.create(),
          MultiplyOperator.create(),
          NegateOperator.create(),
          NotEqualsOperator.create(),
          SizeFunction.create(),
          StartsWithFunction.create(),
          StringFunction.create(),
          SubtractOperator.create(),
          TimestampFunction.create(),
          UintFunction.create());

  /**
   * Enumeration of Standard Function bindings.
   *
   * <p>Note: The conditional, logical_or, logical_and, not_strictly_false, and type functions are
   * currently special-cased, and does not appear in this enum.
   */
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
        Conversions.BOOL_TO_STRING,
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
        Comparison.GREATER_EQUALS_DOUBLE_UINT64);

    /** Container class for CEL standard function overloads. */
    public static final class Overload {

      /** Overloads for internal functions that may have been rewritten by macros (ex: @in) */
      public enum InternalOperator implements StandardOverload {
        IN_LIST(InOverload.IN_LIST::newFunctionBinding),
        IN_MAP(InOverload.IN_MAP::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        InternalOperator(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for functions that test relations. */
      public enum Relation implements StandardOverload {
        EQUALS(EqualsOverload.EQUALS::newFunctionBinding),
        NOT_EQUALS(NotEqualsOverload.NOT_EQUALS::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        Relation(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for performing arithmetic operations. */
      public enum Arithmetic implements StandardOverload {
        ADD_INT64(AddOverload.ADD_INT64::newFunctionBinding),
        ADD_UINT64(AddOverload.ADD_UINT64::newFunctionBinding),
        ADD_BYTES(AddOverload.ADD_BYTES::newFunctionBinding),
        ADD_DOUBLE(AddOverload.ADD_DOUBLE::newFunctionBinding),
        ADD_DURATION_DURATION(AddOverload.ADD_DURATION_DURATION::newFunctionBinding),
        ADD_TIMESTAMP_DURATION(AddOverload.ADD_TIMESTAMP_DURATION::newFunctionBinding),
        ADD_STRING(AddOverload.ADD_STRING::newFunctionBinding),
        ADD_DURATION_TIMESTAMP(AddOverload.ADD_DURATION_TIMESTAMP::newFunctionBinding),
        ADD_LIST(AddOverload.ADD_LIST::newFunctionBinding),

        SUBTRACT_INT64(SubtractOverload.SUBTRACT_INT64::newFunctionBinding),
        SUBTRACT_TIMESTAMP_TIMESTAMP(
            SubtractOverload.SUBTRACT_TIMESTAMP_TIMESTAMP::newFunctionBinding),
        SUBTRACT_TIMESTAMP_DURATION(
            SubtractOverload.SUBTRACT_TIMESTAMP_DURATION::newFunctionBinding),
        SUBTRACT_UINT64(SubtractOverload.SUBTRACT_UINT64::newFunctionBinding),
        SUBTRACT_DOUBLE(SubtractOverload.SUBTRACT_DOUBLE::newFunctionBinding),
        SUBTRACT_DURATION_DURATION(SubtractOverload.SUBTRACT_DURATION_DURATION::newFunctionBinding),

        MULTIPLY_INT64(MultiplyOverload.MULTIPLY_INT64::newFunctionBinding),
        MULTIPLY_DOUBLE(MultiplyOverload.MULTIPLY_DOUBLE::newFunctionBinding),
        MULTIPLY_UINT64(MultiplyOverload.MULTIPLY_UINT64::newFunctionBinding),

        DIVIDE_DOUBLE(DivideOverload.DIVIDE_DOUBLE::newFunctionBinding),
        DIVIDE_INT64(DivideOverload.DIVIDE_INT64::newFunctionBinding),
        DIVIDE_UINT64(DivideOverload.DIVIDE_UINT64::newFunctionBinding),

        MODULO_INT64(ModuloOverload.MODULO_INT64::newFunctionBinding),
        MODULO_UINT64(ModuloOverload.MODULO_UINT64::newFunctionBinding),

        NEGATE_INT64(NegateOverload.NEGATE_INT64::newFunctionBinding),
        NEGATE_DOUBLE(NegateOverload.NEGATE_DOUBLE::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        Arithmetic(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for indexing a list or a map. */
      public enum Index implements StandardOverload {
        INDEX_LIST(IndexOverload.INDEX_LIST::newFunctionBinding),
        INDEX_MAP(IndexOverload.INDEX_MAP::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        Index(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for retrieving the size of a literal or a collection. */
      public enum Size implements StandardOverload {
        SIZE_BYTES(SizeOverload.SIZE_BYTES::newFunctionBinding),
        BYTES_SIZE(SizeOverload.BYTES_SIZE::newFunctionBinding),
        SIZE_LIST(SizeOverload.SIZE_LIST::newFunctionBinding),
        LIST_SIZE(SizeOverload.LIST_SIZE::newFunctionBinding),
        SIZE_STRING(SizeOverload.SIZE_STRING::newFunctionBinding),
        STRING_SIZE(SizeOverload.STRING_SIZE::newFunctionBinding),
        SIZE_MAP(SizeOverload.SIZE_MAP::newFunctionBinding),
        MAP_SIZE(SizeOverload.MAP_SIZE::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        Size(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for performing type conversions. */
      public enum Conversions implements StandardOverload {
        BOOL_TO_BOOL(BoolOverload.BOOL_TO_BOOL::newFunctionBinding),
        STRING_TO_BOOL(BoolOverload.STRING_TO_BOOL::newFunctionBinding),
        INT64_TO_INT64(IntOverload.INT64_TO_INT64::newFunctionBinding),
        DOUBLE_TO_INT64(IntOverload.DOUBLE_TO_INT64::newFunctionBinding),
        STRING_TO_INT64(IntOverload.STRING_TO_INT64::newFunctionBinding),
        TIMESTAMP_TO_INT64(IntOverload.TIMESTAMP_TO_INT64::newFunctionBinding),
        UINT64_TO_INT64(IntOverload.UINT64_TO_INT64::newFunctionBinding),

        UINT64_TO_UINT64(UintOverload.UINT64_TO_UINT64::newFunctionBinding),
        INT64_TO_UINT64(UintOverload.INT64_TO_UINT64::newFunctionBinding),
        DOUBLE_TO_UINT64(UintOverload.DOUBLE_TO_UINT64::newFunctionBinding),
        STRING_TO_UINT64(UintOverload.STRING_TO_UINT64::newFunctionBinding),

        DOUBLE_TO_DOUBLE(DoubleOverload.DOUBLE_TO_DOUBLE::newFunctionBinding),
        INT64_TO_DOUBLE(DoubleOverload.INT64_TO_DOUBLE::newFunctionBinding),
        STRING_TO_DOUBLE(DoubleOverload.STRING_TO_DOUBLE::newFunctionBinding),
        UINT64_TO_DOUBLE(DoubleOverload.UINT64_TO_DOUBLE::newFunctionBinding),

        STRING_TO_STRING(StringOverload.STRING_TO_STRING::newFunctionBinding),
        INT64_TO_STRING(StringOverload.INT64_TO_STRING::newFunctionBinding),
        DOUBLE_TO_STRING(StringOverload.DOUBLE_TO_STRING::newFunctionBinding),
        BOOL_TO_STRING(StringOverload.BOOL_TO_STRING::newFunctionBinding),
        BYTES_TO_STRING(StringOverload.BYTES_TO_STRING::newFunctionBinding),
        TIMESTAMP_TO_STRING(StringOverload.TIMESTAMP_TO_STRING::newFunctionBinding),
        DURATION_TO_STRING(StringOverload.DURATION_TO_STRING::newFunctionBinding),
        UINT64_TO_STRING(StringOverload.UINT64_TO_STRING::newFunctionBinding),

        BYTES_TO_BYTES(BytesOverload.BYTES_TO_BYTES::newFunctionBinding),
        STRING_TO_BYTES(BytesOverload.STRING_TO_BYTES::newFunctionBinding),

        DURATION_TO_DURATION(DurationOverload.DURATION_TO_DURATION::newFunctionBinding),
        STRING_TO_DURATION(DurationOverload.STRING_TO_DURATION::newFunctionBinding),

        STRING_TO_TIMESTAMP(TimestampOverload.STRING_TO_TIMESTAMP::newFunctionBinding),
        TIMESTAMP_TO_TIMESTAMP(TimestampOverload.TIMESTAMP_TO_TIMESTAMP::newFunctionBinding),
        INT64_TO_TIMESTAMP(TimestampOverload.INT64_TO_TIMESTAMP::newFunctionBinding),

        TO_DYN(DynOverload.TO_DYN::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
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
        MATCHES(MatchesOverload.MATCHES::newFunctionBinding),
        MATCHES_STRING(MatchesOverload.MATCHES_STRING::newFunctionBinding),
        CONTAINS_STRING(ContainsOverload.CONTAINS_STRING::newFunctionBinding),
        ENDS_WITH_STRING(EndsWithOverload.ENDS_WITH_STRING::newFunctionBinding),
        STARTS_WITH_STRING(StartsWithOverload.STARTS_WITH_STRING::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        StringMatchers(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for logical operators that return a bool as a result. */
      public enum BooleanOperator implements StandardOverload {
        LOGICAL_NOT(LogicalNotOverload.LOGICAL_NOT::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        BooleanOperator(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for functions performing date/time operations. */
      public enum DateTime implements StandardOverload {
        TIMESTAMP_TO_YEAR(GetFullYearOverload.TIMESTAMP_TO_YEAR::newFunctionBinding),
        TIMESTAMP_TO_YEAR_WITH_TZ(
            GetFullYearOverload.TIMESTAMP_TO_YEAR_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_MONTH(GetMonthOverload.TIMESTAMP_TO_MONTH::newFunctionBinding),
        TIMESTAMP_TO_MONTH_WITH_TZ(GetMonthOverload.TIMESTAMP_TO_MONTH_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_DAY_OF_YEAR(GetDayOfYearOverload.TIMESTAMP_TO_DAY_OF_YEAR::newFunctionBinding),
        TIMESTAMP_TO_DAY_OF_YEAR_WITH_TZ(
            GetDayOfYearOverload.TIMESTAMP_TO_DAY_OF_YEAR_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_DAY_OF_MONTH(
            GetDayOfMonthOverload.TIMESTAMP_TO_DAY_OF_MONTH::newFunctionBinding),
        TIMESTAMP_TO_DAY_OF_MONTH_WITH_TZ(
            GetDayOfMonthOverload.TIMESTAMP_TO_DAY_OF_MONTH_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_DAY_OF_MONTH_1_BASED(
            GetDateOverload.TIMESTAMP_TO_DAY_OF_MONTH_1_BASED::newFunctionBinding),
        TIMESTAMP_TO_DAY_OF_MONTH_1_BASED_WITH_TZ(
            GetDateOverload.TIMESTAMP_TO_DAY_OF_MONTH_1_BASED_WITH_TZ::newFunctionBinding),

        TIMESTAMP_TO_DAY_OF_WEEK(GetDayOfWeekOverload.TIMESTAMP_TO_DAY_OF_WEEK::newFunctionBinding),
        TIMESTAMP_TO_DAY_OF_WEEK_WITH_TZ(
            GetDayOfWeekOverload.TIMESTAMP_TO_DAY_OF_WEEK_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_HOURS(GetHoursOverload.TIMESTAMP_TO_HOURS::newFunctionBinding),
        TIMESTAMP_TO_HOURS_WITH_TZ(GetHoursOverload.TIMESTAMP_TO_HOURS_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_MINUTES(GetMinutesOverload.TIMESTAMP_TO_MINUTES::newFunctionBinding),
        TIMESTAMP_TO_MINUTES_WITH_TZ(
            GetMinutesOverload.TIMESTAMP_TO_MINUTES_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_SECONDS(GetSecondsOverload.TIMESTAMP_TO_SECONDS::newFunctionBinding),
        TIMESTAMP_TO_SECONDS_WITH_TZ(
            GetSecondsOverload.TIMESTAMP_TO_SECONDS_WITH_TZ::newFunctionBinding),
        TIMESTAMP_TO_MILLISECONDS(
            GetMillisecondsOverload.TIMESTAMP_TO_MILLISECONDS::newFunctionBinding),
        TIMESTAMP_TO_MILLISECONDS_WITH_TZ(
            GetMillisecondsOverload.TIMESTAMP_TO_MILLISECONDS_WITH_TZ::newFunctionBinding),
        DURATION_TO_HOURS(GetHoursOverload.DURATION_TO_HOURS::newFunctionBinding),
        DURATION_TO_MINUTES(GetMinutesOverload.DURATION_TO_MINUTES::newFunctionBinding),
        DURATION_TO_SECONDS(GetSecondsOverload.DURATION_TO_SECONDS::newFunctionBinding),
        DURATION_TO_MILLISECONDS(
            GetMillisecondsOverload.DURATION_TO_MILLISECONDS::newFunctionBinding);

        private final FunctionBindingCreator bindingCreator;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        DateTime(FunctionBindingCreator bindingCreator) {
          this.bindingCreator = bindingCreator;
        }
      }

      /** Overloads for performing numeric comparisons. */
      public enum Comparison implements StandardOverload {
        LESS_BOOL(LessOverload.LESS_BOOL::newFunctionBinding, false),
        LESS_INT64(LessOverload.LESS_INT64::newFunctionBinding, false),
        LESS_UINT64(LessOverload.LESS_UINT64::newFunctionBinding, false),
        LESS_BYTES(LessOverload.LESS_BYTES::newFunctionBinding, false),
        LESS_DOUBLE(LessOverload.LESS_DOUBLE::newFunctionBinding, false),
        LESS_DOUBLE_UINT64(LessOverload.LESS_DOUBLE_UINT64::newFunctionBinding, true),
        LESS_INT64_UINT64(LessOverload.LESS_INT64_UINT64::newFunctionBinding, true),
        LESS_UINT64_INT64(LessOverload.LESS_UINT64_INT64::newFunctionBinding, true),
        LESS_INT64_DOUBLE(LessOverload.LESS_INT64_DOUBLE::newFunctionBinding, true),
        LESS_DOUBLE_INT64(LessOverload.LESS_DOUBLE_INT64::newFunctionBinding, true),
        LESS_UINT64_DOUBLE(LessOverload.LESS_UINT64_DOUBLE::newFunctionBinding, true),
        LESS_DURATION(LessOverload.LESS_DURATION::newFunctionBinding, false),
        LESS_STRING(LessOverload.LESS_STRING::newFunctionBinding, false),
        LESS_TIMESTAMP(LessOverload.LESS_TIMESTAMP::newFunctionBinding, false),
        LESS_EQUALS_BOOL(LessEqualsOverload.LESS_EQUALS_BOOL::newFunctionBinding, false),
        LESS_EQUALS_BYTES(LessEqualsOverload.LESS_EQUALS_BYTES::newFunctionBinding, false),
        LESS_EQUALS_DOUBLE(LessEqualsOverload.LESS_EQUALS_DOUBLE::newFunctionBinding, false),
        LESS_EQUALS_DURATION(LessEqualsOverload.LESS_EQUALS_DURATION::newFunctionBinding, false),
        LESS_EQUALS_INT64(LessEqualsOverload.LESS_EQUALS_INT64::newFunctionBinding, false),
        LESS_EQUALS_STRING(LessEqualsOverload.LESS_EQUALS_STRING::newFunctionBinding, false),
        LESS_EQUALS_TIMESTAMP(LessEqualsOverload.LESS_EQUALS_TIMESTAMP::newFunctionBinding, false),
        LESS_EQUALS_UINT64(LessEqualsOverload.LESS_EQUALS_UINT64::newFunctionBinding, false),
        LESS_EQUALS_INT64_UINT64(
            LessEqualsOverload.LESS_EQUALS_INT64_UINT64::newFunctionBinding, true),
        LESS_EQUALS_UINT64_INT64(
            LessEqualsOverload.LESS_EQUALS_UINT64_INT64::newFunctionBinding, true),
        LESS_EQUALS_INT64_DOUBLE(
            LessEqualsOverload.LESS_EQUALS_INT64_DOUBLE::newFunctionBinding, true),
        LESS_EQUALS_DOUBLE_INT64(
            LessEqualsOverload.LESS_EQUALS_DOUBLE_INT64::newFunctionBinding, true),
        LESS_EQUALS_UINT64_DOUBLE(
            LessEqualsOverload.LESS_EQUALS_UINT64_DOUBLE::newFunctionBinding, true),
        LESS_EQUALS_DOUBLE_UINT64(
            LessEqualsOverload.LESS_EQUALS_DOUBLE_UINT64::newFunctionBinding, true),
        GREATER_BOOL(GreaterOverload.GREATER_BOOL::newFunctionBinding, false),
        GREATER_BYTES(GreaterOverload.GREATER_BYTES::newFunctionBinding, false),
        GREATER_DOUBLE(GreaterOverload.GREATER_DOUBLE::newFunctionBinding, false),
        GREATER_DURATION(GreaterOverload.GREATER_DURATION::newFunctionBinding, false),
        GREATER_INT64(GreaterOverload.GREATER_INT64::newFunctionBinding, false),
        GREATER_STRING(GreaterOverload.GREATER_STRING::newFunctionBinding, false),
        GREATER_TIMESTAMP(GreaterOverload.GREATER_TIMESTAMP::newFunctionBinding, false),
        GREATER_UINT64(GreaterOverload.GREATER_UINT64::newFunctionBinding, false),
        GREATER_INT64_UINT64(GreaterOverload.GREATER_INT64_UINT64::newFunctionBinding, true),
        GREATER_UINT64_INT64(GreaterOverload.GREATER_UINT64_INT64::newFunctionBinding, true),
        GREATER_INT64_DOUBLE(GreaterOverload.GREATER_INT64_DOUBLE::newFunctionBinding, true),
        GREATER_DOUBLE_INT64(GreaterOverload.GREATER_DOUBLE_INT64::newFunctionBinding, true),
        GREATER_UINT64_DOUBLE(GreaterOverload.GREATER_UINT64_DOUBLE::newFunctionBinding, true),
        GREATER_DOUBLE_UINT64(GreaterOverload.GREATER_DOUBLE_UINT64::newFunctionBinding, true),
        GREATER_EQUALS_BOOL(GreaterEqualsOverload.GREATER_EQUALS_BOOL::newFunctionBinding, false),
        GREATER_EQUALS_BYTES(GreaterEqualsOverload.GREATER_EQUALS_BYTES::newFunctionBinding, false),
        GREATER_EQUALS_DOUBLE(
            GreaterEqualsOverload.GREATER_EQUALS_DOUBLE::newFunctionBinding, false),
        GREATER_EQUALS_DURATION(
            GreaterEqualsOverload.GREATER_EQUALS_DURATION::newFunctionBinding, false),
        GREATER_EQUALS_INT64(GreaterEqualsOverload.GREATER_EQUALS_INT64::newFunctionBinding, false),
        GREATER_EQUALS_STRING(
            GreaterEqualsOverload.GREATER_EQUALS_STRING::newFunctionBinding, false),
        GREATER_EQUALS_TIMESTAMP(
            GreaterEqualsOverload.GREATER_EQUALS_TIMESTAMP::newFunctionBinding, false),
        GREATER_EQUALS_UINT64(
            GreaterEqualsOverload.GREATER_EQUALS_UINT64::newFunctionBinding, false),
        GREATER_EQUALS_INT64_UINT64(
            GreaterEqualsOverload.GREATER_EQUALS_INT64_UINT64::newFunctionBinding, true),
        GREATER_EQUALS_UINT64_INT64(
            GreaterEqualsOverload.GREATER_EQUALS_UINT64_INT64::newFunctionBinding, true),
        GREATER_EQUALS_INT64_DOUBLE(
            GreaterEqualsOverload.GREATER_EQUALS_INT64_DOUBLE::newFunctionBinding, true),
        GREATER_EQUALS_DOUBLE_INT64(
            GreaterEqualsOverload.GREATER_EQUALS_DOUBLE_INT64::newFunctionBinding, true),
        GREATER_EQUALS_UINT64_DOUBLE(
            GreaterEqualsOverload.GREATER_EQUALS_UINT64_DOUBLE::newFunctionBinding, true),
        GREATER_EQUALS_DOUBLE_UINT64(
            GreaterEqualsOverload.GREATER_EQUALS_DOUBLE_UINT64::newFunctionBinding, true);

        private final FunctionBindingCreator bindingCreator;
        private final boolean isHeterogeneousComparison;

        @Override
        public CelFunctionBinding newFunctionBinding(
            CelOptions celOptions, RuntimeEquality runtimeEquality) {
          return bindingCreator.create(celOptions, runtimeEquality);
        }

        Comparison(FunctionBindingCreator bindingCreator, boolean isHeterogeneousComparison) {
          this.bindingCreator = bindingCreator;
          this.isHeterogeneousComparison = isHeterogeneousComparison;
        }

        public boolean isHeterogeneousComparison() {
          return isHeterogeneousComparison;
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

  @Internal
  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      RuntimeEquality runtimeEquality, CelOptions celOptions) {
    ImmutableSet.Builder<CelFunctionBinding> builder = ImmutableSet.builder();
    for (StandardOverload overload : standardOverloads) {
      builder.add(overload.newFunctionBinding(celOptions, runtimeEquality));
    }

    return builder.build();
  }

  /** General interface for defining a standard function overload. */
  @Immutable
  public interface StandardOverload {
    CelFunctionBinding newFunctionBinding(CelOptions celOptions, RuntimeEquality runtimeEquality);
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

  @FunctionalInterface
  @Immutable
  private interface FunctionBindingCreator {
    CelFunctionBinding create(CelOptions celOptions, RuntimeEquality runtimeEquality);
  }

  private CelStandardFunctions(ImmutableSet<StandardOverload> standardOverloads) {
    this.standardOverloads = standardOverloads;
  }
}
