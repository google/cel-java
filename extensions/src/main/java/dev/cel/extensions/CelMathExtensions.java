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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
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
import dev.cel.parser.CelExprFactory;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelParserBuilder;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
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
final class CelMathExtensions implements CelCompilerLibrary, CelRuntimeLibrary {

  private static final String MATH_NAMESPACE = "math";

  private static final String MATH_MAX_FUNCTION = "math.@max";
  private static final String MATH_MAX_OVERLOAD_DOC =
      "Returns the greatest valued number present in the arguments.";
  private static final String MATH_MIN_FUNCTION = "math.@min";
  private static final String MATH_MIN_OVERLOAD_DOC =
      "Returns the least valued number present in the arguments.";

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

  public enum Function {
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
            CelRuntime.CelFunctionBinding.from("math_@max_double", Double.class, x -> x),
            CelRuntime.CelFunctionBinding.from("math_@max_int", Long.class, x -> x),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_double_double", Double.class, Double.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_int_int", Long.class, Long.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_int_double", Long.class, Double.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_double_int", Double.class, Long.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_list_dyn", List.class, CelMathExtensions::maxList)),
        ImmutableSet.of(
            CelRuntime.CelFunctionBinding.from("math_@max_uint", Long.class, x -> x),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_uint_uint", Long.class, Long.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_double_uint", Double.class, Long.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_uint_int", Long.class, Long.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_uint_double", Long.class, Double.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_int_uint", Long.class, Long.class, CelMathExtensions::maxPair)),
        ImmutableSet.of(
            CelRuntime.CelFunctionBinding.from("math_@max_uint", UnsignedLong.class, x -> x),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_double_uint",
                Double.class,
                UnsignedLong.class,
                CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_uint_int", UnsignedLong.class, Long.class, CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@max_uint_double",
                UnsignedLong.class,
                Double.class,
                CelMathExtensions::maxPair),
            CelRuntime.CelFunctionBinding.from(
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
            CelRuntime.CelFunctionBinding.from("math_@min_double", Double.class, x -> x),
            CelRuntime.CelFunctionBinding.from("math_@min_int", Long.class, x -> x),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_double_double", Double.class, Double.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_int_int", Long.class, Long.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_int_double", Long.class, Double.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_double_int", Double.class, Long.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_list_dyn", List.class, CelMathExtensions::minList)),
        ImmutableSet.of(
            CelRuntime.CelFunctionBinding.from("math_@min_uint", Long.class, x -> x),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_uint_uint", Long.class, Long.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_double_uint", Double.class, Long.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_uint_int", Long.class, Long.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_uint_double", Long.class, Double.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_int_uint", Long.class, Long.class, CelMathExtensions::minPair)),
        ImmutableSet.of(
            CelRuntime.CelFunctionBinding.from("math_@min_uint", UnsignedLong.class, x -> x),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_double_uint",
                Double.class,
                UnsignedLong.class,
                CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_uint_int", UnsignedLong.class, Long.class, CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_uint_double",
                UnsignedLong.class,
                Double.class,
                CelMathExtensions::minPair),
            CelRuntime.CelFunctionBinding.from(
                "math_@min_int_uint", Long.class, UnsignedLong.class, CelMathExtensions::minPair)));

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelRuntime.CelFunctionBinding> functionBindings;
    private final ImmutableSet<CelRuntime.CelFunctionBinding> functionBindingsULongSigned;
    private final ImmutableSet<CelRuntime.CelFunctionBinding> functionBindingsULongUnsigned;

    Function(
        CelFunctionDecl functionDecl,
        ImmutableSet<CelRuntime.CelFunctionBinding> functionBindings,
        ImmutableSet<CelRuntime.CelFunctionBinding> functionBindingsULongSigned,
        ImmutableSet<CelRuntime.CelFunctionBinding> functionBindingsULongUnsigned) {
      this.functionDecl = functionDecl;
      this.functionBindings = functionBindings;
      this.functionBindingsULongSigned = functionBindingsULongSigned;
      this.functionBindingsULongUnsigned = functionBindingsULongUnsigned;
    }
  }

  private final boolean enableUnsignedLongs;
  private final ImmutableSet<Function> functions;

  CelMathExtensions(CelOptions celOptions) {
    this(celOptions, ImmutableSet.copyOf(Function.values()));
  }

  CelMathExtensions(CelOptions celOptions, Set<Function> functions) {
    this.enableUnsignedLongs = celOptions.enableUnsignedLongs();
    this.functions = ImmutableSet.copyOf(functions);
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
      CelExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
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
      CelExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
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
      CelExprFactory exprFactory, String functionName, List<CelExpr> arguments) {

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
      CelExprFactory exprFactory, String functionName, CelExpr argument) {
    if (argument.exprKind().getKind() == Kind.CREATE_LIST) {
      if (argument.createList().elements().isEmpty()) {
        return newError(
            exprFactory, String.format("%s invalid single argument value", functionName), argument);
      }

      return checkInvalidArgument(exprFactory, functionName, argument.createList().elements());
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
    } else if (argument.exprKind().getKind().equals(Kind.CREATE_LIST)
        || argument.exprKind().getKind().equals(Kind.CREATE_STRUCT)
        || argument.exprKind().getKind().equals(Kind.CREATE_MAP)) {
      return false;
    }

    return true;
  }

  private static Optional<CelExpr> newError(
      CelExprFactory exprFactory, String errorMessage, CelExpr argument) {
    return Optional.of(
        exprFactory.reportError(
            CelIssue.formatError(exprFactory.getSourceLocation(argument), errorMessage)));
  }
}
