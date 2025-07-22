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

package dev.cel.extensions;

import static com.google.common.collect.Comparators.max;
import static com.google.common.collect.Comparators.min;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Internal implementation of Math Extensions
 *
 * <p>Note: For equal numbers with different types, the result is always the first argument e.g.:
 * math.greatest(1u, 1.0) -> 1u
 */
@SuppressWarnings({"rawtypes", "unchecked"}) // Use of raw Comparables.
@Immutable
final class CelMathExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary {

  private static final String MATH_NAMESPACE = "math";

  private static final String MATH_MAX_FUNCTION = "math.@max";
  private static final String MATH_MAX_OVERLOAD_DOC =
      "Returns the greatest valued number present in the arguments.";
  private static final String MATH_MIN_FUNCTION = "math.@min";
  private static final String MATH_MIN_OVERLOAD_DOC =
      "Returns the least valued number present in the arguments.";

  // Rounding Functions
  private static final String MATH_CEIL_FUNCTION = "math.ceil";
  private static final String MATH_FLOOR_FUNCTION = "math.floor";
  private static final String MATH_ROUND_FUNCTION = "math.round";
  private static final String MATH_TRUNC_FUNCTION = "math.trunc";

  // Floating Point Functions
  private static final String MATH_ISFINITE_FUNCTION = "math.isFinite";
  private static final String MATH_ISNAN_FUNCTION = "math.isNaN";
  private static final String MATH_ISINF_FUNCTION = "math.isInf";

  // Signedness Functions
  private static final String MATH_ABS_FUNCTION = "math.abs";
  private static final String MATH_SIGN_FUNCTION = "math.sign";

  // Bitwise Functions
  private static final String MATH_BIT_AND_FUNCTION = "math.bitAnd";
  private static final String MATH_BIT_OR_FUNCTION = "math.bitOr";
  private static final String MATH_BIT_XOR_FUNCTION = "math.bitXor";
  private static final String MATH_BIT_NOT_FUNCTION = "math.bitNot";
  private static final String MATH_BIT_LEFT_SHIFT_FUNCTION = "math.bitShiftLeft";
  private static final String MATH_BIT_RIGHT_SHIFT_FUNCTION = "math.bitShiftRight";

  private static final String MATH_SQRT_FUNCTION = "math.sqrt";

  private static final int MAX_BIT_SHIFT = 63;

  /**
   * Returns the proper comparison function to use for a math function call involving different
   * argument types.
   *
   * <p>Example: (uint, int) -> {@link ComparisonFunctions#compareUintInt(UnsignedLong, long)}
   */
  private static final ImmutableTable<Class, Class, BiFunction<Object, Object, Integer>>
      CLASSES_TO_COMPARATORS = newComparatorTable();

  private static ImmutableTable<Class, Class, BiFunction<Object, Object, Integer>>
      newComparatorTable() {
    ImmutableTable.Builder<Class, Class, BiFunction<Object, Object, Integer>> builder =
        new ImmutableTable.Builder<>();
    builder.put(
        Long.class,
        Double.class,
        (x, y) -> ComparisonFunctions.compareIntDouble((Long) x, (Double) y));
    builder.put(
        Double.class,
        Long.class,
        (x, y) -> ComparisonFunctions.compareDoubleInt((Double) x, (Long) y));
    builder.put(
        Double.class,
        UnsignedLong.class,
        (x, y) -> ComparisonFunctions.compareDoubleUint((Double) x, (UnsignedLong) y));
    builder.put(
        UnsignedLong.class,
        Double.class,
        (x, y) -> ComparisonFunctions.compareUintDouble((UnsignedLong) x, (Double) y));
    builder.put(
        Long.class,
        UnsignedLong.class,
        (x, y) -> ComparisonFunctions.compareIntUint((Long) x, (UnsignedLong) y));
    builder.put(
        UnsignedLong.class,
        Long.class,
        (x, y) -> ComparisonFunctions.compareUintInt((UnsignedLong) x, (Long) y));
    return builder.buildOrThrow();
  }

  enum Function {
    MAX(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_MAX_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double", MATH_MAX_OVERLOAD_DOC, SimpleType.DOUBLE, SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int", MATH_MAX_OVERLOAD_DOC, SimpleType.INT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint", MATH_MAX_OVERLOAD_DOC, SimpleType.UINT, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double_double",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int_int",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint_uint",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int_uint",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int_double",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double_int",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double_uint",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint_int",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint_double",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_list_dyn", // Implementation supports double, int and uint as list
                // literals. Anything else will error during macro expansion.
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                ListType.create(SimpleType.DYN))),
        ImmutableSet.of(
            CelFunctionBinding.from("math_@max_double", Double.class, x -> x),
            CelFunctionBinding.from("math_@max_int", Long.class, x -> x),
            CelFunctionBinding.from(
                "math_@max_double_double", Double.class, Double.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_int_int", Long.class, Long.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_int_double", Long.class, Double.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_double_int", Double.class, Long.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_list_dyn", List.class, CelMathExtensions::maxList)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_@max_uint", Long.class, x -> x),
            CelFunctionBinding.from(
                "math_@max_uint_uint", Long.class, Long.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_double_uint", Double.class, Long.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_uint_int", Long.class, Long.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_uint_double", Long.class, Double.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_int_uint", Long.class, Long.class, CelMathExtensions::maxPair)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_@max_uint", UnsignedLong.class, x -> x),
            CelFunctionBinding.from(
                "math_@max_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_double_uint",
                Double.class,
                UnsignedLong.class,
                CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_uint_int", UnsignedLong.class, Long.class, CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_uint_double",
                UnsignedLong.class,
                Double.class,
                CelMathExtensions::maxPair),
            CelFunctionBinding.from(
                "math_@max_int_uint", Long.class, UnsignedLong.class, CelMathExtensions::maxPair))),
    MIN(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_MIN_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double", MATH_MIN_OVERLOAD_DOC, SimpleType.DOUBLE, SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int", MATH_MIN_OVERLOAD_DOC, SimpleType.INT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint", MATH_MIN_OVERLOAD_DOC, SimpleType.UINT, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double_double",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int_int",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint_uint",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int_uint",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int_double",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double_int",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double_uint",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint_int",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint_double",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_list_dyn", // Implementation supports double, int and uint as list
                // literals. Anything else will error during macro expansion.
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                ListType.create(SimpleType.DYN))),
        ImmutableSet.of(
            CelFunctionBinding.from("math_@min_double", Double.class, x -> x),
            CelFunctionBinding.from("math_@min_int", Long.class, x -> x),
            CelFunctionBinding.from(
                "math_@min_double_double", Double.class, Double.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_int_int", Long.class, Long.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_int_double", Long.class, Double.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_double_int", Double.class, Long.class, CelMathExtensions::minPair),
            CelFunctionBinding.from("math_@min_list_dyn", List.class, CelMathExtensions::minList)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_@min_uint", Long.class, x -> x),
            CelFunctionBinding.from(
                "math_@min_uint_uint", Long.class, Long.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_double_uint", Double.class, Long.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_uint_int", Long.class, Long.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_uint_double", Long.class, Double.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_int_uint", Long.class, Long.class, CelMathExtensions::minPair)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_@min_uint", UnsignedLong.class, x -> x),
            CelFunctionBinding.from(
                "math_@min_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_double_uint",
                Double.class,
                UnsignedLong.class,
                CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_uint_int", UnsignedLong.class, Long.class, CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_uint_double",
                UnsignedLong.class,
                Double.class,
                CelMathExtensions::minPair),
            CelFunctionBinding.from(
                "math_@min_int_uint", Long.class, UnsignedLong.class, CelMathExtensions::minPair))),
    CEIL(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_CEIL_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_ceil_double",
                "Compute the ceiling of a double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE)),
        ImmutableSet.of(CelFunctionBinding.from("math_ceil_double", Double.class, Math::ceil))),
    FLOOR(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_FLOOR_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_floor_double",
                "Compute the floor of a double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE)),
        ImmutableSet.of(CelFunctionBinding.from("math_floor_double", Double.class, Math::floor))),
    ROUND(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ROUND_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_round_double",
                "Rounds the double value to the nearest whole number with ties rounding away from"
                    + " zero.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_round_double", Double.class, CelMathExtensions::round))),
    TRUNC(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_TRUNC_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_trunc_double",
                "Truncates the fractional portion of the double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_trunc_double", Double.class, CelMathExtensions::trunc))),
    ISFINITE(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ISFINITE_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_isFinite_double",
                "Returns true if the value is a finite number.",
                SimpleType.BOOL,
                SimpleType.DOUBLE)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_isFinite_double", Double.class, Double::isFinite))),
    ISNAN(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ISNAN_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_isNaN_double",
                "Returns true if the input double value is NaN, false otherwise.",
                SimpleType.BOOL,
                SimpleType.DOUBLE)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_isNaN_double", Double.class, CelMathExtensions::isNaN))),
    ISINF(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ISINF_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_isInf_double",
                "Returns true if the input double value is -Inf or +Inf.",
                SimpleType.BOOL,
                SimpleType.DOUBLE)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_isInf_double", Double.class, CelMathExtensions::isInfinite))),
    ABS(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ABS_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_abs_double",
                "Compute the absolute value of a double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_abs_int",
                "Compute the absolute value of an int value.",
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_abs_uint",
                "Compute the absolute value of a uint value.",
                SimpleType.UINT,
                SimpleType.UINT)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_abs_double", Double.class, Math::abs),
            CelFunctionBinding.from("math_abs_int", Long.class, CelMathExtensions::absExact),
            CelFunctionBinding.from("math_abs_uint", UnsignedLong.class, x -> x))),
    SIGN(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_SIGN_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_sign_double",
                "Returns the sign of the input numeric type, either -1, 0, 1 cast as double.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_sign_uint",
                "Returns the sign of the input numeric type, either -1, 0, 1 case as uint.",
                SimpleType.UINT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_sign_int",
                "Returns the sign of the input numeric type, either -1, 0, 1.",
                SimpleType.INT,
                SimpleType.INT)),
        ImmutableSet.of(
            CelFunctionBinding.from("math_sign_double", Double.class, CelMathExtensions::sign),
            CelFunctionBinding.from("math_sign_int", Long.class, CelMathExtensions::sign),
            CelFunctionBinding.from(
                "math_sign_uint", UnsignedLong.class, CelMathExtensions::sign))),
    BITAND(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_AND_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitAnd_int_int",
                "Performs a bitwise-AND operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitAnd_uint_uint",
                "Performs a bitwise-AND operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitAnd_int_int", Long.class, Long.class, CelMathExtensions::intBitAnd),
            CelFunctionBinding.from(
                "math_bitAnd_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathExtensions::uintBitAnd))),
    BITOR(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_OR_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitOr_int_int",
                "Performs a bitwise-OR operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitOr_uint_uint",
                "Performs a bitwise-OR operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitOr_int_int", Long.class, Long.class, CelMathExtensions::intBitOr),
            CelFunctionBinding.from(
                "math_bitOr_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathExtensions::uintBitOr))),
    BITXOR(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_XOR_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitXor_int_int",
                "Performs a bitwise-XOR operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitXor_uint_uint",
                "Performs a bitwise-XOR operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitXor_int_int", Long.class, Long.class, CelMathExtensions::intBitXor),
            CelFunctionBinding.from(
                "math_bitXor_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathExtensions::uintBitXor))),
    BITNOT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_NOT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitNot_int_int",
                "Performs a bitwise-NOT operation over two int values.",
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitNot_uint_uint",
                "Performs a bitwise-NOT operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitNot_int_int", Long.class, CelMathExtensions::intBitNot),
            CelFunctionBinding.from(
                "math_bitNot_uint_uint", UnsignedLong.class, CelMathExtensions::uintBitNot))),
    BITSHIFTLEFT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_LEFT_SHIFT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftLeft_int_int",
                "Performs a bitwise-SHIFTLEFT operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftLeft_uint_int",
                "Performs a bitwise-SHIFTLEFT operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.INT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitShiftLeft_int_int",
                Long.class,
                Long.class,
                CelMathExtensions::intBitShiftLeft),
            CelFunctionBinding.from(
                "math_bitShiftLeft_uint_int",
                UnsignedLong.class,
                Long.class,
                CelMathExtensions::uintBitShiftLeft))),
    BITSHIFTRIGHT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_RIGHT_SHIFT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftRight_int_int",
                "Performs a bitwise-SHIFTRIGHT operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftRight_uint_int",
                "Performs a bitwise-SHIFTRIGHT operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.INT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitShiftRight_int_int",
                Long.class,
                Long.class,
                CelMathExtensions::intBitShiftRight),
            CelFunctionBinding.from(
                "math_bitShiftRight_uint_int",
                UnsignedLong.class,
                Long.class,
                CelMathExtensions::uintBitShiftRight))),
    SQRT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_SQRT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_sqrt_double",
                "Computes square root of the double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_sqrt_int",
                "Computes square root of the int value.",
                SimpleType.DOUBLE,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_sqrt_uint",
                "Computes square root of the unsigned value.",
                SimpleType.DOUBLE,
                SimpleType.UINT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_sqrt_double", Double.class, CelMathExtensions::sqrtDouble),
            CelFunctionBinding.from(
                "math_sqrt_int", Long.class, CelMathExtensions::sqrtInt),
            CelFunctionBinding.from(
                "math_sqrt_uint", UnsignedLong.class, CelMathExtensions::sqrtUint)));

    private static final ImmutableSet<Function> VERSION_0 = ImmutableSet.of(
        MIN,
        MAX);

    private static final ImmutableSet<Function> VERSION_1 =
        ImmutableSet.<Function>builder()
            .addAll(VERSION_0)
            .add(
                CEIL,
                FLOOR,
                ROUND,
                TRUNC,
                ISINF,
                ISNAN,
                ISFINITE,
                ABS,
                SIGN,
                BITAND,
                BITOR,
                BITXOR,
                BITNOT,
                BITSHIFTLEFT,
                BITSHIFTRIGHT)
            .build();

    private static final ImmutableSet<Function> VERSION_2 =
        ImmutableSet.<Function>builder()
            .addAll(VERSION_1)
            .add(SQRT)
            .build();

    private static final ImmutableSet<Function> VERSION_LATEST = VERSION_2;

    private static ImmutableSet<Function> byVersion(int version) {
      switch (version) {
        case 0:
          return Function.VERSION_0;
        case 1:
          return Function.VERSION_1;
        case 2:
          return Function.VERSION_2;
        case Integer.MAX_VALUE:
          return Function.VERSION_LATEST;
        default:
          throw new IllegalArgumentException("Unsupported 'math' extension version " + version);
      }
    }

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelFunctionBinding> functionBindings;
    private final ImmutableSet<CelFunctionBinding> functionBindingsULongSigned;
    private final ImmutableSet<CelFunctionBinding> functionBindingsULongUnsigned;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, ImmutableSet<CelFunctionBinding> bindings) {
      this(functionDecl, bindings, ImmutableSet.of(), ImmutableSet.of());
    }

    Function(
        CelFunctionDecl functionDecl,
        ImmutableSet<CelFunctionBinding> functionBindings,
        ImmutableSet<CelFunctionBinding> functionBindingsULongSigned,
        ImmutableSet<CelFunctionBinding> functionBindingsULongUnsigned) {
      this.functionDecl = functionDecl;
      this.functionBindings = functionBindings;
      this.functionBindingsULongSigned = functionBindingsULongSigned;
      this.functionBindingsULongUnsigned = functionBindingsULongUnsigned;
    }
  }

  private final boolean enableUnsignedLongs;
  private final ImmutableSet<Function> functions;
  private final int version;

  CelMathExtensions(CelOptions celOptions, int version) {
    this(celOptions, version, Function.byVersion(version));
  }

  CelMathExtensions(CelOptions celOptions, Set<Function> functions) {
    this(celOptions, -1, functions);
  }

  private CelMathExtensions(CelOptions celOptions, int version, Set<Function> functions) {
    this.enableUnsignedLongs = celOptions.enableUnsignedLongs();
    this.version = version;
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public String getName() {
    return "math";
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> getFunctions() {
    return functions.stream().map(f -> f.functionDecl).collect(toImmutableSet());
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    parserBuilder.addMacros(
        CelMacro.newReceiverVarArgMacro("greatest", CelMathExtensions::expandGreatestMacro),
        CelMacro.newReceiverVarArgMacro("least", CelMathExtensions::expandLeastMacro));
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    functions.forEach(
        function -> {
          runtimeBuilder.addFunctionBindings(function.functionBindings);
          runtimeBuilder.addFunctionBindings(
              enableUnsignedLongs
                  ? function.functionBindingsULongUnsigned
                  : function.functionBindingsULongSigned);
        });
  }

  private static Optional<CelExpr> expandGreatestMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    if (!isTargetInNamespace(target)) {
      // Return empty to indicate that we're not interested in expanding this macro, and
      // that the parser should default to a function call on the receiver.
      return Optional.empty();
    }

    switch (arguments.size()) {
      case 0:
        return newError(exprFactory, "math.greatest() requires at least one argument", target);
      case 1:
        Optional<CelExpr> invalidArg =
            checkInvalidArgumentSingleArg(exprFactory, "math.greatest()", arguments.get(0));
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MAX_FUNCTION, arguments.get(0)));
      case 2:
        invalidArg = checkInvalidArgument(exprFactory, "math.greatest()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MAX_FUNCTION, arguments));
      default:
        invalidArg = checkInvalidArgument(exprFactory, "math.greatest()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(
            exprFactory.newGlobalCall(MATH_MAX_FUNCTION, exprFactory.newList(arguments)));
    }
  }

  private static Comparable maxPair(Comparable x, Comparable y) {
    if (x.getClass().equals(y.getClass())) {
      return max(x, y);
    }

    return CLASSES_TO_COMPARATORS.get(x.getClass(), y.getClass()).apply(x, y) >= 0 ? x : y;
  }

  private static Comparable maxList(List<Comparable> list) {
    if (list.isEmpty()) {
      throw new IllegalStateException("math.@max(list) argument must not be empty");
    }

    Comparable max = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      max = maxPair(max, list.get(i));
    }

    return max;
  }

  private static Comparable minPair(Comparable x, Comparable y) {
    if (x.getClass().equals(y.getClass())) {
      return min(x, y);
    }

    return CLASSES_TO_COMPARATORS.get(x.getClass(), y.getClass()).apply(x, y) <= 0 ? x : y;
  }

  private static long absExact(long x) {
    if (x == Long.MIN_VALUE) {
      // The only case where standard Math.abs overflows silently
      throw new ArithmeticException("integer overflow");
    }
    return Math.abs(x);
  }

  private static boolean isNaN(double x) {
    return Double.isNaN(x);
  }

  private static Double trunc(Double x) {
    if (isNaN(x) || isInfinite(x)) {
      return x;
    }
    return (double) x.longValue();
  }

  private static boolean isInfinite(double x) {
    return Double.isInfinite(x);
  }

  private static double round(double x) {
    if (isNaN(x) || isInfinite(x)) {
      return x;
    }
    return DoubleMath.roundToLong(x, RoundingMode.HALF_EVEN);
  }

  private static Number sign(Number x) {
    if (x instanceof Double) {
      double val = x.doubleValue();
      if (isNaN(val)) {
        return val;
      }
      if (val == 0) {
        return 0.0;
      }
      return val > 0 ? 1.0 : -1.0;
    }

    if (x instanceof Long) {
      long val = x.longValue();
      if (val == 0) {
        return 0L;
      }
      return val > 0 ? 1L : -1L;
    }

    if (x instanceof UnsignedLong) {
      UnsignedLong val = (UnsignedLong) x;
      if (val.equals(UnsignedLong.ZERO)) {
        return val;
      }
      return UnsignedLong.ONE;
    }

    throw new IllegalArgumentException("Unsupported type: " + x.getClass());
  }

  private static Long intBitAnd(long x, long y) {
    return x & y;
  }

  private static UnsignedLong uintBitAnd(UnsignedLong x, UnsignedLong y) {
    return UnsignedLong.fromLongBits(x.longValue() & y.longValue());
  }

  private static Long intBitOr(long x, long y) {
    return x | y;
  }

  private static UnsignedLong uintBitOr(UnsignedLong x, UnsignedLong y) {
    return UnsignedLong.fromLongBits(x.longValue() | y.longValue());
  }

  private static Long intBitXor(long x, long y) {
    return x ^ y;
  }

  private static UnsignedLong uintBitXor(UnsignedLong x, UnsignedLong y) {
    return UnsignedLong.fromLongBits(x.longValue() ^ y.longValue());
  }

  private static Long intBitNot(long x) {
    return ~x;
  }

  private static UnsignedLong uintBitNot(UnsignedLong x) {
    return UnsignedLong.fromLongBits(~x.longValue());
  }

  private static Long intBitShiftLeft(long value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftLeft() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return 0L;
    }
    return value << shiftAmount;
  }

  private static UnsignedLong uintBitShiftLeft(UnsignedLong value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftLeft() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return UnsignedLong.ZERO;
    }
    return UnsignedLong.fromLongBits(value.longValue() << shiftAmount);
  }

  private static Long intBitShiftRight(long value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftRight() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return 0L;
    }
    return value >>> shiftAmount;
  }

  private static UnsignedLong uintBitShiftRight(UnsignedLong value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftRight() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return UnsignedLong.ZERO;
    }
    return UnsignedLong.fromLongBits(value.longValue() >>> shiftAmount);
  }

  private static Double sqrtDouble(double x) {
    return Math.sqrt(x);
  }

  private static Double sqrtInt(Long x) {
    return sqrtDouble(x.doubleValue());
  }

  private static Double sqrtUint(UnsignedLong x) {
    return sqrtDouble(x.doubleValue());
  }

  private static Comparable minList(List<Comparable> list) {
    if (list.isEmpty()) {
      throw new IllegalStateException("math.@min(list) argument must not be empty");
    }

    Comparable min = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      min = minPair(min, list.get(i));
    }

    return min;
  }

  private static Optional<CelExpr> expandLeastMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    if (!isTargetInNamespace(target)) {
      // Return empty to indicate that we're not interested in expanding this macro, and
      // that the parser should default to a function call on the receiver.
      return Optional.empty();
    }

    switch (arguments.size()) {
      case 0:
        return newError(exprFactory, "math.least() requires at least one argument", target);
      case 1:
        Optional<CelExpr> invalidArg =
            checkInvalidArgumentSingleArg(exprFactory, "math.least()", arguments.get(0));
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MIN_FUNCTION, arguments.get(0)));
      case 2:
        invalidArg = checkInvalidArgument(exprFactory, "math.least()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MIN_FUNCTION, arguments));
      default:
        invalidArg = checkInvalidArgument(exprFactory, "math.least()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(
            exprFactory.newGlobalCall(MATH_MIN_FUNCTION, exprFactory.newList(arguments)));
    }
  }

  private static boolean isTargetInNamespace(CelExpr target) {
    return target.exprKind().getKind().equals(Kind.IDENT)
        && target.ident().name().equals(MATH_NAMESPACE);
  }

  private static Optional<CelExpr> checkInvalidArgument(
      CelMacroExprFactory exprFactory, String functionName, List<CelExpr> arguments) {

    for (CelExpr arg : arguments) {
      if (!isArgumentValidType(arg)) {
        return newError(
            exprFactory,
            String.format("%s simple literal arguments must be numeric", functionName),
            arg);
      }
    }
    return Optional.empty();
  }

  private static Optional<CelExpr> checkInvalidArgumentSingleArg(
      CelMacroExprFactory exprFactory, String functionName, CelExpr argument) {
    if (argument.exprKind().getKind() == Kind.LIST) {
      if (argument.list().elements().isEmpty()) {
        return newError(
            exprFactory, String.format("%s invalid single argument value", functionName), argument);
      }

      return checkInvalidArgument(exprFactory, functionName, argument.list().elements());
    }
    if (isArgumentValidType(argument)) {
      return Optional.empty();
    }

    return newError(
        exprFactory, String.format("%s invalid single argument value", functionName), argument);
  }

  private static boolean isArgumentValidType(CelExpr argument) {
    if (argument.exprKind().getKind() == Kind.CONSTANT) {
      CelConstant constant = argument.constant();
      return constant.getKind() == CelConstant.Kind.INT64_VALUE
          || constant.getKind() == CelConstant.Kind.UINT64_VALUE
          || constant.getKind() == CelConstant.Kind.DOUBLE_VALUE;
    } else if (argument.exprKind().getKind().equals(Kind.LIST)
        || argument.exprKind().getKind().equals(Kind.STRUCT)
        || argument.exprKind().getKind().equals(Kind.MAP)) {
      return false;
    }

    return true;
  }

  private static Optional<CelExpr> newError(
      CelMacroExprFactory exprFactory, String errorMessage, CelExpr argument) {
    return Optional.of(
        exprFactory.reportError(
            CelIssue.formatError(exprFactory.getSourceLocation(argument), errorMessage)));
  }
}
