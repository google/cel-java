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

import dev.cel.expr.ExprValue;
import dev.cel.expr.UnknownSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.annotations.Internal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Util class for CEL interpreter.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class InterpreterUtil {

  /**
   * Enforces strictness. The outcome of a failed computation is represented by the value being a
   * {@link Throwable}. Applying {@code strict()} to such a value-or-throwable will re-throw the
   * proper exception.
   */
  public static Object strict(Object valueOrThrowable) throws InterpreterException {
    if (!(valueOrThrowable instanceof Throwable)) {
      return valueOrThrowable;
    }
    if (valueOrThrowable instanceof InterpreterException) {
      throw (InterpreterException) valueOrThrowable;
    }
    if (valueOrThrowable instanceof RuntimeException) {
      throw (RuntimeException) valueOrThrowable;
    }
    throw new RuntimeException((Throwable) valueOrThrowable);
  }

  /**
   * Check if raw object is ExprValue object and has UnknownSet
   *
   * @param obj Object to check.
   * @return boolean value if object is unknown.
   */
  public static boolean isUnknown(Object obj) {
    return obj instanceof ExprValue
        && ((ExprValue) obj).getKindCase() == ExprValue.KindCase.UNKNOWN;
  }

  /**
   * Throws an InterpreterException with {@code exceptionMessage} if the {@code obj} is an instance
   * of {@link IncompleteData}. {@link IncompleteData} does not support some operators.
   *
   * <p>Returns the obj argument otherwise.
   *
   * <p>Deprecated. TODO: Can be removed once clients have stopped using
   * IncompleteData.
   */
  @CanIgnoreReturnValue
  @Deprecated
  public static Object completeDataOnly(Object obj, String exceptionMessage)
      throws InterpreterException {
    if (obj instanceof IncompleteData) {
      throw new InterpreterException.Builder(exceptionMessage).build();
    }
    return obj;
  }

  /**
   * Combine multiple ExprValue objects which has UnknownSet into one ExprValue
   *
   * @param objs ExprValue objects which has UnknownSet
   * @return A new ExprValue object which has all unknown expr ids from input objects, without
   *     duplication.
   */
  public static ExprValue combineUnknownExprValue(Object... objs) {
    UnknownSet.Builder unknownsetBuilder = UnknownSet.newBuilder();
    Set<Long> ids = new LinkedHashSet<>();
    for (Object object : objs) {
      if (isUnknown(object)) {
        ids.addAll(((ExprValue) object).getUnknown().getExprsList());
      }
    }
    unknownsetBuilder.addAllExprs(ids);
    return ExprValue.newBuilder().setUnknown(unknownsetBuilder).build();
  }

  /** Create a {@code ExprValue} for one or more {@code ids} representing an unknown set. */
  public static ExprValue createUnknownExprValue(Long... ids) {
    return createUnknownExprValue(Arrays.asList(ids));
  }

  /**
   * Create an ExprValue object has UnknownSet, from a list of unknown expr ids
   *
   * @param ids List of unknown expr ids
   * @return A new ExprValue object which has all unknown expr ids from input list
   */
  public static ExprValue createUnknownExprValue(List<Long> ids) {
    ExprValue.Builder exprValueBuilder = ExprValue.newBuilder();
    exprValueBuilder.setUnknown(UnknownSet.newBuilder().addAllExprs(ids));
    return exprValueBuilder.build();
  }

  /**
   * Short circuit unknown or error arguments to logical operators.
   *
   * <p>Given two arguments, one of which must be throwable (error) or unknown, returns the result
   * from the && or || operators for these arguments, assuming that the result cannot be determined
   * from any boolean arguments alone. This allows us to consolidate the error/unknown handling for
   * both of these operators.
   */
  public static Object shortcircuitUnknownOrThrowable(Object left, Object right)
      throws InterpreterException {
    // unknown <op> unknown ==> unknown combined
    if (InterpreterUtil.isUnknown(left) && InterpreterUtil.isUnknown(right)) {
      return InterpreterUtil.combineUnknownExprValue(left, right);
    }
    // unknown <op> <error> ==> unknown
    // unknown <op> t|f ==> unknown
    if (InterpreterUtil.isUnknown(left)) {
      return left;
    }
    // <error> <op> unknown ==> unknown
    // t|f <op> unknown ==> unknown
    if (InterpreterUtil.isUnknown(right)) {
      return right;
    }
    // Throw left or right side exception for now, should combine them into ErrorSet.
    // <error> <op> <error> ==> <error>
    if (left instanceof Throwable) {
      return InterpreterUtil.strict(left);
    }
    if (right instanceof Throwable) {
      return InterpreterUtil.strict(right);
    }
    throw new RuntimeException(
        "Left or/and right object is neither bool, unknown nor error, unexpected behavior.");
  }

  public static Object valueOrUnknown(@Nullable Object valueOrThrowable, Long id) {
    // Handle the unknown value case.
    if (isUnknown(valueOrThrowable)) {
      ExprValue value = (ExprValue) valueOrThrowable;
      if (value.getUnknown().getExprsCount() != 0) {
        return valueOrThrowable;
      }
      return createUnknownExprValue(id);
    }
    // Handle the null value case.
    if (valueOrThrowable == null) {
      return createUnknownExprValue(id);
    }
    return valueOrThrowable;
  }

  private InterpreterUtil() {}
}
