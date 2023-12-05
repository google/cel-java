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

package dev.cel.parser;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelIssue;
import dev.cel.common.ast.CelExpr;
import java.util.Optional;

/** Describes a function signature to match and the {@link CelMacroExpander} to apply. */
@AutoValue
@Immutable
public abstract class CelMacro implements Comparable<CelMacro> {

  private static final String ACCUMULATOR_VAR = "__result__";

  /** Field presence test macro */
  public static final CelMacro HAS =
      newGlobalMacro(Operator.HAS.getFunction(), 1, CelMacro::expandHasMacro);

  /**
   * Boolean comprehension which asserts that a predicate holds true for all elements in the input
   * range.
   */
  public static final CelMacro ALL =
      newReceiverMacro(Operator.ALL.getFunction(), 2, CelMacro::expandAllMacro);

  /**
   * Boolean comprehension which asserts that a predicate holds true for at least one element in the
   * input range.
   */
  public static final CelMacro EXISTS =
      newReceiverMacro(Operator.EXISTS.getFunction(), 2, CelMacro::expandExistsMacro);

  /**
   * Boolean comprehension which asserts that a predicate holds true for exactly one element in the
   * input range.
   */
  public static final CelMacro EXISTS_ONE =
      newReceiverMacro(Operator.EXISTS_ONE.getFunction(), 2, CelMacro::expandExistsOneMacro);

  /**
   * Comprehension which applies a transform to each element in the input range and produces a list
   * of equivalent size as output.
   */
  public static final CelMacro MAP =
      newReceiverMacro(Operator.MAP.getFunction(), 2, CelMacro::expandMapMacro);

  /**
   * Comprehension which conditionally applies a transform to elements in the list which satisfy the
   * filter predicate.
   */
  public static final CelMacro MAP_FILTER =
      newReceiverMacro(Operator.MAP.getFunction(), 3, CelMacro::expandMapMacro);

  /**
   * Comprehension which produces a list containing elements in the input range which match the
   * filter.
   */
  public static final CelMacro FILTER =
      newReceiverMacro(Operator.FILTER.getFunction(), 2, CelMacro::expandFilterMacro);

  /** Set of all standard macros supported by the CEL spec. */
  public static final ImmutableList<CelMacro> STANDARD_MACROS =
      ImmutableList.of(HAS, ALL, EXISTS, EXISTS_ONE, MAP, MAP_FILTER, FILTER);

  // Package-private default constructor to prevent extensions outside of the codebase.
  CelMacro() {}

  /** Returns the function name for this macro. */
  public abstract String getFunction();

  /** Returns the number of arguments this macro expects. For variadic macros, this is 0. */
  public abstract int getArgumentCount();

  abstract boolean getReceiverStyle();

  /** True if this macro is receiver-style, false if it is global. */
  public final boolean isReceiverStyle() {
    return getReceiverStyle();
  }

  /** Returns the unique string used to identify this macro. */
  public abstract String getKey();

  abstract boolean getVariadic();

  /** Returns true if this macro accepts any number of arguments, false otherwise. */
  public final boolean isVariadic() {
    return getVariadic();
  }

  /** Returns the expander for this macro. */
  public abstract CelMacroExpander getExpander();

  @Override
  public final int compareTo(CelMacro other) {
    if (other == null) {
      return 1;
    }
    int diff = getFunction().compareTo(other.getFunction());
    if (diff != 0) {
      return diff;
    }
    diff = Boolean.compare(!isVariadic(), !other.isVariadic());
    if (diff != 0) {
      return diff;
    }
    if (!isVariadic()) {
      diff = Integer.compare(getArgumentCount(), other.getArgumentCount());
      if (diff != 0) {
        return diff;
      }
    }
    return Boolean.compare(isReceiverStyle(), other.isReceiverStyle());
  }

  @Override
  public final String toString() {
    return getKey();
  }

  @Override
  public final int hashCode() {
    return getKey().hashCode();
  }

  @Override
  public final boolean equals(Object other) {
    return other instanceof CelMacro && getKey().equals(((CelMacro) other).getKey());
  }

  static Builder newBuilder() {
    return new AutoValue_CelMacro.Builder();
  }

  /** Creates a new global macro that accepts a fixed number of arguments. */
  public static CelMacro newGlobalMacro(String function, int argCount, CelMacroExpander expander) {
    checkArgument(!isNullOrEmpty(function));
    checkArgument(argCount >= 0);
    checkNotNull(expander);
    return newBuilder()
        .setFunction(function)
        .setArgumentCount(argCount)
        .setReceiverStyle(false)
        .setKey(formatKey(function, argCount, false))
        .setVariadic(false)
        .setExpander(expander)
        .build();
  }

  /** Creates a new global macro that accepts a variable number of arguments. */
  public static CelMacro newGlobalVarArgMacro(String function, CelMacroExpander expander) {
    checkArgument(!isNullOrEmpty(function));
    checkNotNull(expander);
    return newBuilder()
        .setFunction(function)
        .setArgumentCount(0)
        .setReceiverStyle(false)
        .setKey(formatVarArgKey(function, false))
        .setVariadic(true)
        .setExpander(expander)
        .build();
  }

  /** Creates a new receiver-style macro that accepts a fixed number of arguments. */
  public static CelMacro newReceiverMacro(
      String function, int argCount, CelMacroExpander expander) {
    checkArgument(!isNullOrEmpty(function));
    checkArgument(argCount >= 0);
    checkNotNull(expander);
    return newBuilder()
        .setFunction(function)
        .setArgumentCount(argCount)
        .setReceiverStyle(true)
        .setKey(formatKey(function, argCount, true))
        .setVariadic(false)
        .setExpander(expander)
        .build();
  }

  /** Creates a new receiver-style macro that accepts a variable number of arguments. */
  public static CelMacro newReceiverVarArgMacro(String function, CelMacroExpander expander) {
    checkArgument(!isNullOrEmpty(function));
    checkNotNull(expander);
    return newBuilder()
        .setFunction(function)
        .setArgumentCount(0)
        .setReceiverStyle(true)
        .setKey(formatVarArgKey(function, true))
        .setVariadic(true)
        .setExpander(expander)
        .build();
  }

  static String formatKey(String function, int argCount, boolean receiverStyle) {
    checkArgument(!isNullOrEmpty(function));
    checkArgument(argCount >= 0);
    return String.format("%s:%d:%s", function, argCount, receiverStyle);
  }

  static String formatVarArgKey(String function, boolean receiverStyle) {
    checkArgument(!isNullOrEmpty(function));
    return String.format("%s:*:%s", function, receiverStyle);
  }

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setFunction(String function);

    abstract Builder setArgumentCount(int argumentCount);

    abstract Builder setReceiverStyle(boolean receiverStyle);

    abstract Builder setKey(String key);

    abstract Builder setVariadic(boolean variadic);

    abstract Builder setExpander(CelMacroExpander expander);

    @CheckReturnValue
    abstract CelMacro build();
  }

  // CelMacroExpander implementation for CEL's has() macro.
  private static Optional<CelExpr> expandHasMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 1);
    CelExpr arg = checkNotNull(arguments.get(0));
    if (arg.exprKind().getKind() != CelExpr.ExprKind.Kind.SELECT) {
      return Optional.of(exprFactory.reportError("invalid argument to has() macro"));
    }
    return Optional.of(exprFactory.newSelect(arg.select().operand(), arg.select().field(), true));
  }

  // CelMacroExpander implementation for CEL's all() macro.
  private static Optional<CelExpr> expandAllMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 2);
    CelExpr arg0 = checkNotNull(arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg0));
    }
    CelExpr arg1 = checkNotNull(arguments.get(1));
    CelExpr accuInit = exprFactory.newBoolLiteral(true);
    CelExpr condition =
        exprFactory.newGlobalCall(
            Operator.NOT_STRICTLY_FALSE.getFunction(), exprFactory.newIdentifier(ACCUMULATOR_VAR));
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.LOGICAL_AND.getFunction(), exprFactory.newIdentifier(ACCUMULATOR_VAR), arg1);
    CelExpr result = exprFactory.newIdentifier(ACCUMULATOR_VAR);
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(), target, ACCUMULATOR_VAR, accuInit, condition, step, result));
  }

  // CelMacroExpander implementation for CEL's exists() macro.
  private static Optional<CelExpr> expandExistsMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 2);
    CelExpr arg0 = checkNotNull(arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg0));
    }
    CelExpr arg1 = checkNotNull(arguments.get(1));
    CelExpr accuInit = exprFactory.newBoolLiteral(false);
    CelExpr condition =
        exprFactory.newGlobalCall(
            Operator.NOT_STRICTLY_FALSE.getFunction(),
            exprFactory.newGlobalCall(
                Operator.LOGICAL_NOT.getFunction(), exprFactory.newIdentifier(ACCUMULATOR_VAR)));
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.LOGICAL_OR.getFunction(), exprFactory.newIdentifier(ACCUMULATOR_VAR), arg1);
    CelExpr result = exprFactory.newIdentifier(ACCUMULATOR_VAR);
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(), target, ACCUMULATOR_VAR, accuInit, condition, step, result));
  }

  // CelMacroExpander implementation for CEL's exists_one() macro.
  private static Optional<CelExpr> expandExistsOneMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 2);
    CelExpr arg0 = checkNotNull(arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg0));
    }
    CelExpr arg1 = checkNotNull(arguments.get(1));
    CelExpr zeroExpr = exprFactory.newIntLiteral(0);
    CelExpr oneExpr = exprFactory.newIntLiteral(1);
    CelExpr accuInit = zeroExpr;
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.CONDITIONAL.getFunction(),
            arg1,
            exprFactory.newGlobalCall(
                Operator.ADD.getFunction(), exprFactory.newIdentifier(ACCUMULATOR_VAR), oneExpr),
            exprFactory.newIdentifier(ACCUMULATOR_VAR));
    CelExpr result =
        exprFactory.newGlobalCall(
            Operator.EQUALS.getFunction(), exprFactory.newIdentifier(ACCUMULATOR_VAR), oneExpr);
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(), target, ACCUMULATOR_VAR, accuInit, condition, step, result));
  }

  // CelMacroExpander implementation for CEL's map() macro.
  private static Optional<CelExpr> expandMapMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 2 || arguments.size() == 3);
    CelExpr arg0 = checkNotNull(arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(
          exprFactory.reportError(
              CelIssue.formatError(
                  exprFactory.getSourceLocation(arg0), "argument is not an identifier")));
    }
    CelExpr arg1;
    CelExpr arg2;
    if (arguments.size() == 3) {
      arg2 = checkNotNull(arguments.get(1));
      arg1 = checkNotNull(arguments.get(2));
    } else {
      arg1 = checkNotNull(arguments.get(1));
      arg2 = null;
    }
    CelExpr accuInit = exprFactory.newList();
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.ADD.getFunction(),
            exprFactory.newIdentifier(ACCUMULATOR_VAR),
            exprFactory.newList(arg1));
    if (arg2 != null) {
      step =
          exprFactory.newGlobalCall(
              Operator.CONDITIONAL.getFunction(),
              arg2,
              step,
              exprFactory.newIdentifier(ACCUMULATOR_VAR));
    }
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            target,
            ACCUMULATOR_VAR,
            accuInit,
            condition,
            step,
            exprFactory.newIdentifier(ACCUMULATOR_VAR)));
  }

  // CelMacroExpander implementation for CEL's filter() macro.
  private static Optional<CelExpr> expandFilterMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 2);
    CelExpr arg0 = checkNotNull(arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg0));
    }
    CelExpr arg1 = checkNotNull(arguments.get(1));
    CelExpr accuInit = exprFactory.newList();
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.ADD.getFunction(),
            exprFactory.newIdentifier(ACCUMULATOR_VAR),
            exprFactory.newList(arg0));
    step =
        exprFactory.newGlobalCall(
            Operator.CONDITIONAL.getFunction(),
            arg1,
            step,
            exprFactory.newIdentifier(ACCUMULATOR_VAR));
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            target,
            ACCUMULATOR_VAR,
            accuInit,
            condition,
            step,
            exprFactory.newIdentifier(ACCUMULATOR_VAR)));
  }

  private static CelExpr reportArgumentError(CelMacroExprFactory exprFactory, CelExpr argument) {
    return exprFactory.reportError(
        CelIssue.formatError(
            exprFactory.getSourceLocation(argument), "The argument must be a simple name"));
  }
}
