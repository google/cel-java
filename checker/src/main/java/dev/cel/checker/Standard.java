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

package dev.cel.checker;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.common.types.TypeType;
import dev.cel.parser.Operator;

/**
 * Standard declarations for CEL.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class Standard {

  private static final ImmutableList<CelFunctionDecl> CORE_FUNCTION_DECLARATIONS =
      coreFunctionDeclarations();
  private static final ImmutableList<CelIdentDecl> CORE_IDENT_DECLARATIONS =
      coreIdentDeclarations();

  /**
   * Adds the standard declarations of CEL to the environment.
   *
   * <p>Note: Standard declarations should be provided in their own scope to avoid collisions with
   * custom declarations. The {@link Env#standard} helper method does this by default.
   */
  @CanIgnoreReturnValue
  public static Env add(Env env) {
    CORE_FUNCTION_DECLARATIONS.forEach(env::add);
    CORE_IDENT_DECLARATIONS.forEach(env::add);

    // TODO: Remove this flag guard once the feature has been auto-enabled.
    timestampConversionDeclarations(env.enableTimestampEpoch()).forEach(env::add);
    numericComparisonDeclarations(env.enableHeterogeneousNumericComparisons()).forEach(env::add);

    return env;
  }

  /** Do the expensive work of setting up all the objects in the environment. */
  private static ImmutableList<CelIdentDecl> coreIdentDeclarations() {
    ImmutableList.Builder<CelIdentDecl> identDeclBuilder = ImmutableList.builder();

    // Type Denotations
    for (CelType type :
        ImmutableList.of(
            SimpleType.INT,
            SimpleType.UINT,
            SimpleType.BOOL,
            SimpleType.DOUBLE,
            SimpleType.BYTES,
            SimpleType.STRING,
            SimpleType.DYN)) {
      identDeclBuilder.add(
          CelIdentDecl.newBuilder()
              .setName(CelTypes.format(type))
              .setType(TypeType.create(type))
              .setDoc("type denotation")
              .build());
    }
    identDeclBuilder.add(
        CelIdentDecl.newBuilder()
            .setName("type")
            .setType(TypeType.create(SimpleType.DYN))
            .setDoc("type denotation")
            .build());
    identDeclBuilder.add(
        CelIdentDecl.newBuilder()
            .setName("null_type")
            .setType(TypeType.create(SimpleType.NULL_TYPE))
            .setDoc("type denotation")
            .build());
    identDeclBuilder.add(
        CelIdentDecl.newBuilder()
            .setName("list")
            .setType(TypeType.create(ListType.create(SimpleType.DYN)))
            .setDoc("type denotation")
            .build());
    identDeclBuilder.add(
        CelIdentDecl.newBuilder()
            .setName("map")
            .setType(TypeType.create(MapType.create(SimpleType.DYN, SimpleType.DYN)))
            .setDoc("type denotation")
            .build());

    return identDeclBuilder.build();
  }

  /** Do the expensive work of setting up all the objects in the environment. */
  private static ImmutableList<CelFunctionDecl> coreFunctionDeclarations() {
    // Some shortcuts we use when building declarations.
    TypeParamType typeParamA = TypeParamType.create("A");
    ListType listOfA = ListType.create(typeParamA);
    TypeParamType typeParamB = TypeParamType.create("B");
    MapType mapOfAb = MapType.create(typeParamA, typeParamB);

    ImmutableList.Builder<CelFunctionDecl> celFunctionDeclBuilder = ImmutableList.builder();

    // Booleans
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.CONDITIONAL.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "conditional",
                "conditional",
                typeParamA,
                SimpleType.BOOL,
                typeParamA,
                typeParamA)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.LOGICAL_AND.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "logical_and", "logical_and", SimpleType.BOOL, SimpleType.BOOL, SimpleType.BOOL)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.LOGICAL_OR.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "logical_or", "logical or", SimpleType.BOOL, SimpleType.BOOL, SimpleType.BOOL)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.LOGICAL_NOT.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "logical_not", "logical not", SimpleType.BOOL, SimpleType.BOOL)));
    CelFunctionDecl notStrictlyFalse =
        CelFunctionDecl.newFunctionDeclaration(
            Operator.OLD_NOT_STRICTLY_FALSE.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "not_strictly_false",
                "false if argument is false, true otherwise (including errors and unknowns)",
                SimpleType.BOOL,
                SimpleType.BOOL));
    celFunctionDeclBuilder.add(notStrictlyFalse);
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newBuilder()
            .setName(Operator.NOT_STRICTLY_FALSE.getFunction())
            .addOverloads(sameAs(notStrictlyFalse, "", ""))
            .build());

    // Relations
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.EQUALS.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "equals", "equality", SimpleType.BOOL, typeParamA, typeParamA)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.NOT_EQUALS.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "not_equals", "inequality", SimpleType.BOOL, typeParamA, typeParamA)));

    // Algebra
    CelFunctionDecl commonArithmetic =
        CelFunctionDecl.newFunctionDeclaration(
            Operator.SUBTRACT.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "common_int64", "arithmetic", SimpleType.INT, SimpleType.INT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "common_uint64", "arithmetic", SimpleType.UINT, SimpleType.UINT, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "common_double",
                "arithmetic",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE));
    CelFunctionDecl subtract =
        CelFunctionDecl.newBuilder()
            .setName(Operator.SUBTRACT.getFunction())
            .addOverloads(sameAs(commonArithmetic, "common", "subtract"))
            .addOverloads(
                CelOverloadDecl.newGlobalOverload(
                    "subtract_timestamp_timestamp",
                    "arithmetic",
                    SimpleType.DURATION,
                    SimpleType.TIMESTAMP,
                    SimpleType.TIMESTAMP),
                CelOverloadDecl.newGlobalOverload(
                    "subtract_timestamp_duration",
                    "arithmetic",
                    SimpleType.TIMESTAMP,
                    SimpleType.TIMESTAMP,
                    SimpleType.DURATION),
                CelOverloadDecl.newGlobalOverload(
                    "subtract_duration_duration",
                    "arithmetic",
                    SimpleType.DURATION,
                    SimpleType.DURATION,
                    SimpleType.DURATION))
            .build();
    celFunctionDeclBuilder.add(subtract);
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newBuilder()
            .setName(Operator.MULTIPLY.getFunction())
            .addOverloads(sameAs(commonArithmetic, "common", "multiply"))
            .build());
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newBuilder()
            .setName(Operator.DIVIDE.getFunction())
            .addOverloads(sameAs(commonArithmetic, "common", "divide"))
            .build());
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.MODULO.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "modulo_int64", "arithmetic", SimpleType.INT, SimpleType.INT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "modulo_uint64", "arithmetic", SimpleType.UINT, SimpleType.UINT, SimpleType.UINT)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newBuilder()
            .setName(Operator.ADD.getFunction())
            .addOverloads(sameAs(commonArithmetic, "common", "add"))
            .addOverloads(
                CelOverloadDecl.newGlobalOverload(
                    "add_string",
                    "string concatenation",
                    SimpleType.STRING,
                    SimpleType.STRING,
                    SimpleType.STRING),
                CelOverloadDecl.newGlobalOverload(
                    "add_bytes",
                    "bytes concatenation",
                    SimpleType.BYTES,
                    SimpleType.BYTES,
                    SimpleType.BYTES),
                CelOverloadDecl.newGlobalOverload(
                    "add_list", "list concatenation", listOfA, listOfA, listOfA),
                CelOverloadDecl.newGlobalOverload(
                    "add_timestamp_duration",
                    "arithmetic",
                    SimpleType.TIMESTAMP,
                    SimpleType.TIMESTAMP,
                    SimpleType.DURATION),
                CelOverloadDecl.newGlobalOverload(
                    "add_duration_timestamp",
                    "arithmetic",
                    SimpleType.TIMESTAMP,
                    SimpleType.DURATION,
                    SimpleType.TIMESTAMP),
                CelOverloadDecl.newGlobalOverload(
                    "add_duration_duration",
                    "arithmetic",
                    SimpleType.DURATION,
                    SimpleType.DURATION,
                    SimpleType.DURATION))
            .build());
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.NEGATE.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "negate_int64", "negation", SimpleType.INT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "negate_double", "negation", SimpleType.DOUBLE, SimpleType.DOUBLE)));

    // Index
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            Operator.INDEX.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "index_list", "list indexing", typeParamA, listOfA, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "index_map", "map indexing", typeParamB, mapOfAb, typeParamA)));

    // Collections
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "size",
            CelOverloadDecl.newGlobalOverload(
                "size_string", "string length", SimpleType.INT, SimpleType.STRING),
            CelOverloadDecl.newGlobalOverload(
                "size_bytes", "bytes length", SimpleType.INT, SimpleType.BYTES),
            CelOverloadDecl.newGlobalOverload("size_list", "list size", SimpleType.INT, listOfA),
            CelOverloadDecl.newGlobalOverload("size_map", "map size", SimpleType.INT, mapOfAb),
            CelOverloadDecl.newMemberOverload(
                "string_size", "string length", SimpleType.INT, SimpleType.STRING),
            CelOverloadDecl.newMemberOverload(
                "bytes_size", "bytes length", SimpleType.INT, SimpleType.BYTES),
            CelOverloadDecl.newMemberOverload("list_size", "list size", SimpleType.INT, listOfA),
            CelOverloadDecl.newMemberOverload("map_size", "map size", SimpleType.INT, mapOfAb)));

    // Set membership 'in' operator.
    CelFunctionDecl inOperator =
        CelFunctionDecl.newFunctionDeclaration(
            Operator.OLD_IN.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "in_list", "list membership", SimpleType.BOOL, typeParamA, listOfA),
            CelOverloadDecl.newGlobalOverload(
                "in_map", "map key membership", SimpleType.BOOL, typeParamA, mapOfAb));
    celFunctionDeclBuilder.add(inOperator);
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newBuilder()
            .setName(Operator.IN.getFunction())
            .addOverloads(sameAs(inOperator, "", ""))
            .build());

    // Conversions to type
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "type",
            CelOverloadDecl.newGlobalOverload(
                "type", "returns type of value", TypeType.create(typeParamA), typeParamA)));

    // Conversions to int
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "int",
            CelOverloadDecl.newGlobalOverload(
                "uint64_to_int64", "type conversion", SimpleType.INT, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "double_to_int64", "type conversion", SimpleType.INT, SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "string_to_int64", "type conversion", SimpleType.INT, SimpleType.STRING),
            CelOverloadDecl.newGlobalOverload(
                "timestamp_to_int64",
                "Convert timestamp to int64 in seconds since Unix epoch.",
                SimpleType.INT,
                SimpleType.TIMESTAMP)));

    // Conversions to uint
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "uint",
            CelOverloadDecl.newGlobalOverload(
                "int64_to_uint64", "type conversion", SimpleType.UINT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "double_to_uint64", "type conversion", SimpleType.UINT, SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "string_to_uint64", "type conversion", SimpleType.UINT, SimpleType.STRING)));

    // Conversions to double
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "double",
            CelOverloadDecl.newGlobalOverload(
                "int64_to_double", "type conversion", SimpleType.DOUBLE, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "uint64_to_double", "type conversion", SimpleType.DOUBLE, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "string_to_double", "type conversion", SimpleType.DOUBLE, SimpleType.STRING)));

    // Conversions to string
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "string",
            CelOverloadDecl.newGlobalOverload(
                "int64_to_string", "type conversion", SimpleType.STRING, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "uint64_to_string", "type conversion", SimpleType.STRING, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "double_to_string", "type conversion", SimpleType.STRING, SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "bytes_to_string", "type conversion", SimpleType.STRING, SimpleType.BYTES),
            CelOverloadDecl.newGlobalOverload(
                "timestamp_to_string", "type_conversion", SimpleType.STRING, SimpleType.TIMESTAMP),
            CelOverloadDecl.newGlobalOverload(
                "duration_to_string", "type_conversion", SimpleType.STRING, SimpleType.DURATION)));

    // Conversions to list
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "list",
            CelOverloadDecl.newGlobalOverload(
                "to_list", "type conversion", listOfA, TypeType.create(typeParamA), listOfA)));

    // Conversions to map
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "map",
            CelOverloadDecl.newGlobalOverload(
                "to_map",
                "type conversion",
                mapOfAb,
                TypeType.create(typeParamA),
                TypeType.create(typeParamB),
                mapOfAb)));

    // Conversions to bytes
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "bytes",
            CelOverloadDecl.newGlobalOverload(
                "string_to_bytes", "type conversion", SimpleType.BYTES, SimpleType.STRING)));

    // Conversions to dyn
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "dyn",
            CelOverloadDecl.newGlobalOverload(
                "to_dyn", "type conversion", SimpleType.DYN, typeParamA)));

    // Conversions to Duration
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "duration",
            CelOverloadDecl.newGlobalOverload(
                "string_to_duration",
                "type conversion, duration should be end with \"s\", which stands for seconds",
                SimpleType.DURATION,
                SimpleType.STRING)));

    // String functions
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "matches",
            CelOverloadDecl.newGlobalOverload(
                "matches",
                "matches first argument against regular expression in second argument",
                SimpleType.BOOL,
                SimpleType.STRING,
                SimpleType.STRING)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "matches",
            CelOverloadDecl.newMemberOverload(
                "matches_string",
                "matches the self argument against regular expression in first argument",
                SimpleType.BOOL,
                SimpleType.STRING,
                SimpleType.STRING)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "contains",
            CelOverloadDecl.newMemberOverload(
                "contains_string",
                "tests whether the string operand contains the substring",
                SimpleType.BOOL,
                SimpleType.STRING,
                SimpleType.STRING)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "endsWith",
            CelOverloadDecl.newMemberOverload(
                "ends_with_string",
                "tests whether the string operand ends with the suffix argument",
                SimpleType.BOOL,
                SimpleType.STRING,
                SimpleType.STRING)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "startsWith",
            CelOverloadDecl.newMemberOverload(
                "starts_with_string",
                "tests whether the string operand starts with the prefix argument",
                SimpleType.BOOL,
                SimpleType.STRING,
                SimpleType.STRING)));

    // Date/time functions
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getFullYear",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_year",
                "get year from the date in UTC",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_year_with_tz",
                "get year from the date with timezone",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getMonth",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_month",
                "get month from the date in UTC, 0-11",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_month_with_tz",
                "get month from the date with timezone, 0-11",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getDayOfYear",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_year",
                "get day of year from the date in UTC, zero-based indexing",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_year_with_tz",
                "get day of year from the date with timezone, zero-based indexing",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getDayOfMonth",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_month",
                "get day of month from the date in UTC, zero-based indexing",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_month_with_tz",
                "get day of month from the date with timezone, zero-based indexing",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING)));
    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getDate",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_month_1_based",
                "get day of month from the date in UTC, one-based indexing",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_month_1_based_with_tz",
                "get day of month from the date with timezone, one-based indexing",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getDayOfWeek",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_week",
                "get day of week from the date in UTC, zero-based, zero for Sunday",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_day_of_week_with_tz",
                "get day of week from the date with timezone, zero-based, zero for Sunday",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getHours",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_hours",
                "get hours from the date in UTC, 0-23",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_hours_with_tz",
                "get hours from the date with timezone, 0-23",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING),
            CelOverloadDecl.newMemberOverload(
                "duration_to_hours",
                "get hours from duration",
                SimpleType.INT,
                SimpleType.DURATION)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getMinutes",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_minutes",
                "get minutes from the date in UTC, 0-59",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_minutes_with_tz",
                "get minutes from the date with timezone, 0-59",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING),
            CelOverloadDecl.newMemberOverload(
                "duration_to_minutes",
                "get minutes from duration",
                SimpleType.INT,
                SimpleType.DURATION)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getSeconds",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_seconds",
                "get seconds from the date in UTC, 0-59",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_seconds_with_tz",
                "get seconds from the date with timezone, 0-59",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING),
            CelOverloadDecl.newMemberOverload(
                "duration_to_seconds",
                "get seconds from duration",
                SimpleType.INT,
                SimpleType.DURATION)));

    celFunctionDeclBuilder.add(
        CelFunctionDecl.newFunctionDeclaration(
            "getMilliseconds",
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_milliseconds",
                "get milliseconds from the date in UTC, 0-999",
                SimpleType.INT,
                SimpleType.TIMESTAMP),
            CelOverloadDecl.newMemberOverload(
                "timestamp_to_milliseconds_with_tz",
                "get milliseconds from the date with timezone, 0-999",
                SimpleType.INT,
                SimpleType.TIMESTAMP,
                SimpleType.STRING),
            CelOverloadDecl.newMemberOverload(
                "duration_to_milliseconds",
                "milliseconds from duration, 0-999",
                SimpleType.INT,
                SimpleType.DURATION)));

    return celFunctionDeclBuilder.build();
  }

  private static ImmutableList<CelFunctionDecl> timestampConversionDeclarations(boolean withEpoch) {
    CelFunctionDecl.Builder timestampBuilder =
        CelFunctionDecl.newBuilder()
            .setName("timestamp")
            .addOverloads(
                CelOverloadDecl.newGlobalOverload(
                    "string_to_timestamp",
                    "Type conversion of strings to timestamps according to RFC3339. Example:"
                        + " \"1972-01-01T10:00:20.021-05:00\".",
                    SimpleType.TIMESTAMP,
                    SimpleType.STRING));
    if (withEpoch) {
      timestampBuilder.addOverloads(
          CelOverloadDecl.newGlobalOverload(
              "int64_to_timestamp",
              "Type conversion of integers as Unix epoch seconds to timestamps.",
              SimpleType.TIMESTAMP,
              SimpleType.INT));
    }
    return ImmutableList.of(timestampBuilder.build());
  }

  private static ImmutableList<CelFunctionDecl> numericComparisonDeclarations(
      boolean withHeterogeneousComparisons) {
    CelFunctionDecl.Builder lessBuilder =
        CelFunctionDecl.newBuilder()
            .setName(Operator.LESS.getFunction())
            .addOverloads(
                CelOverloadDecl.newGlobalOverload(
                    "less_bool", "ordering", SimpleType.BOOL, SimpleType.BOOL, SimpleType.BOOL),
                CelOverloadDecl.newGlobalOverload(
                    "less_int64", "ordering", SimpleType.BOOL, SimpleType.INT, SimpleType.INT),
                CelOverloadDecl.newGlobalOverload(
                    "less_uint64", "ordering", SimpleType.BOOL, SimpleType.UINT, SimpleType.UINT),
                CelOverloadDecl.newGlobalOverload(
                    "less_double",
                    "ordering",
                    SimpleType.BOOL,
                    SimpleType.DOUBLE,
                    SimpleType.DOUBLE),
                CelOverloadDecl.newGlobalOverload(
                    "less_string",
                    "ordering",
                    SimpleType.BOOL,
                    SimpleType.STRING,
                    SimpleType.STRING),
                CelOverloadDecl.newGlobalOverload(
                    "less_bytes", "ordering", SimpleType.BOOL, SimpleType.BYTES, SimpleType.BYTES),
                CelOverloadDecl.newGlobalOverload(
                    "less_timestamp",
                    "ordering",
                    SimpleType.BOOL,
                    SimpleType.TIMESTAMP,
                    SimpleType.TIMESTAMP),
                CelOverloadDecl.newGlobalOverload(
                    "less_duration",
                    "ordering",
                    SimpleType.BOOL,
                    SimpleType.DURATION,
                    SimpleType.DURATION));

    if (withHeterogeneousComparisons) {
      lessBuilder.addOverloads(
          CelOverloadDecl.newGlobalOverload(
              "less_int64_uint64",
              "Compare a signed integer value to an unsigned integer value",
              SimpleType.BOOL,
              SimpleType.INT,
              SimpleType.UINT),
          CelOverloadDecl.newGlobalOverload(
              "less_uint64_int64",
              "Compare an unsigned integer value to a signed integer value",
              SimpleType.BOOL,
              SimpleType.UINT,
              SimpleType.INT),
          CelOverloadDecl.newGlobalOverload(
              "less_int64_double",
              "Compare a signed integer value to a double value, coalesces the integer to a double",
              SimpleType.BOOL,
              SimpleType.INT,
              SimpleType.DOUBLE),
          CelOverloadDecl.newGlobalOverload(
              "less_double_int64",
              "Compare a double value to a signed integer value, coalesces the integer to a double",
              SimpleType.BOOL,
              SimpleType.DOUBLE,
              SimpleType.INT),
          CelOverloadDecl.newGlobalOverload(
              "less_uint64_double",
              "Compare an unsigned integer value to a double value, coalesces the unsigned integer"
                  + " to a double",
              SimpleType.BOOL,
              SimpleType.UINT,
              SimpleType.DOUBLE),
          CelOverloadDecl.newGlobalOverload(
              "less_double_uint64",
              "Compare a double value to an unsigned integer value, coalesces the unsigned integer"
                  + " to a double",
              SimpleType.BOOL,
              SimpleType.DOUBLE,
              SimpleType.UINT));
    }

    CelFunctionDecl less = lessBuilder.build();
    return ImmutableList.of(
        less,
        CelFunctionDecl.newBuilder()
            .setName(Operator.LESS_EQUALS.getFunction())
            .addOverloads(sameAs(less, "less", "less_equals"))
            .build(),
        CelFunctionDecl.newBuilder()
            .setName(Operator.GREATER.getFunction())
            .addOverloads(sameAs(less, "less", "greater"))
            .build(),
        CelFunctionDecl.newBuilder()
            .setName(Operator.GREATER_EQUALS.getFunction())
            .addOverloads(sameAs(less, "less", "greater_equals"))
            .build());
  }

  /**
   * Add the overloads of another function to this function, after replacing the overload id as
   * specified.
   */
  private static ImmutableList<CelOverloadDecl> sameAs(
      CelFunctionDecl func, String idPart, String idPartReplace) {
    ImmutableList.Builder<CelOverloadDecl> overloads = new ImmutableList.Builder<>();
    Preconditions.checkNotNull(func);
    for (CelOverloadDecl overload : func.overloads()) {
      overloads.add(
          overload.toBuilder()
              .setOverloadId(overload.overloadId().replace(idPart, idPartReplace))
              .build());
    }
    return overloads.build();
  }

  private Standard() {}
}
