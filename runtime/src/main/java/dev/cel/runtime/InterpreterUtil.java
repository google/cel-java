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

import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.annotations.Internal;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

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
  @CheckReturnValue
  public static Object strict(Object valueOrThrowable) throws CelEvaluationException {
    if (!(valueOrThrowable instanceof Throwable)) {
      return valueOrThrowable;
    }
    if (valueOrThrowable instanceof CelEvaluationException) {
      throw (CelEvaluationException) valueOrThrowable;
    }
    if (valueOrThrowable instanceof RuntimeException) {
      throw (RuntimeException) valueOrThrowable;
    }
    throw new RuntimeException((Throwable) valueOrThrowable);
  }

  /**
   * Check if raw object is {@link CelUnknownSet}.
   *
   * @param obj Object to check.
   * @return boolean value if object is unknown.
   */
  public static boolean isUnknown(Object obj) {
    return obj instanceof CelUnknownSet;
  }

  static boolean isAccumulatedUnknowns(Object obj) {
    return obj instanceof AccumulatedUnknowns;
  }

  /** If the argument is {@link CelUnknownSet}, adapts it into {@link AccumulatedUnknowns} */
  static Object maybeAdaptToAccumulatedUnknowns(Object val) {
    if (!(val instanceof CelUnknownSet)) {
      return val;
    }

    return adaptToAccumulatedUnknowns((CelUnknownSet) val);
  }

  static AccumulatedUnknowns adaptToAccumulatedUnknowns(CelUnknownSet unknowns) {
    return AccumulatedUnknowns.create(unknowns.unknownExprIds(), unknowns.attributes());
  }

  static AccumulatedUnknowns combineUnknownExprValue(Object... objs) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Object object : objs) {
      if (isAccumulatedUnknowns(object)) {
        ids.addAll(((AccumulatedUnknowns) object).exprIds());
      }
    }

    return AccumulatedUnknowns.create(ids);
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
      throws CelEvaluationException {
    // unknown <op> unknown ==> unknown combined
    if (InterpreterUtil.isAccumulatedUnknowns(left)
        && InterpreterUtil.isAccumulatedUnknowns(right)) {
      return InterpreterUtil.combineUnknownExprValue(left, right);
    }
    // unknown <op> <error> ==> unknown
    // unknown <op> t|f ==> unknown
    if (InterpreterUtil.isAccumulatedUnknowns(left)) {
      return left;
    }
    // <error> <op> unknown ==> unknown
    // t|f <op> unknown ==> unknown
    if (InterpreterUtil.isAccumulatedUnknowns(right)) {
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
    if (isAccumulatedUnknowns(valueOrThrowable)) {
      return AccumulatedUnknowns.create(id);
    }
    // Handle the null value case.
    if (valueOrThrowable == null) {
      return AccumulatedUnknowns.create(id);
    }
    return valueOrThrowable;
  }

  private InterpreterUtil() {}
}
