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

package dev.cel.runtime.planner;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.NullValue;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;

@Immutable
final class EvalConstant implements Interpretable {

  // Pre-allocation of common constants
  private static final EvalConstant NULL_VALUE = new EvalConstant(NullValue.NULL_VALUE);
  private static final EvalConstant TRUE = new EvalConstant(true);
  private static final EvalConstant FALSE = new EvalConstant(false);
  private static final EvalConstant ZERO = new EvalConstant(0L);
  private static final EvalConstant ONE = new EvalConstant(1L);
  private static final EvalConstant UNSIGNED_ZERO = new EvalConstant(UnsignedLong.ZERO);
  private static final EvalConstant UNSIGNED_ONE = new EvalConstant(UnsignedLong.ONE);
  private static final EvalConstant EMPTY_STRING = new EvalConstant("");
  private static final EvalConstant EMPTY_BYTES = new EvalConstant(CelByteString.EMPTY);

  @SuppressWarnings("Immutable") // Known CEL constants that aren't mutated are stored
  private final Object constant;

  @Override
  public Object eval(GlobalResolver resolver) {
    return constant;
  }

  @Override
  public Object eval(GlobalResolver resolver, CelEvaluationListener listener) {
    return constant;
  }

  @Override
  public Object eval(GlobalResolver resolver, CelFunctionResolver lateBoundFunctionResolver) {
    return constant;
  }

  @Override
  public Object eval(
      GlobalResolver resolver,
      CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener) {
    return constant;
  }

  static EvalConstant create(boolean value) {
    return value ? TRUE : FALSE;
  }

  static EvalConstant create(String value) {
    if (value.isEmpty()) {
      return EMPTY_STRING;
    }

    return new EvalConstant(value);
  }

  static EvalConstant create(long value) {
    if (value == 0L) {
      return ZERO;
    } else if (value == 1L) {
      return ONE;
    }

    return new EvalConstant(Long.valueOf(value));
  }

  static EvalConstant create(double value) {
    return new EvalConstant(Double.valueOf(value));
  }

  static EvalConstant create(UnsignedLong unsignedLong) {
    if (unsignedLong.longValue() == 0L) {
      return UNSIGNED_ZERO;
    } else if (unsignedLong.longValue() == 1L) {
      return UNSIGNED_ONE;
    }

    return new EvalConstant(unsignedLong);
  }

  static EvalConstant create(NullValue unused) {
    return NULL_VALUE;
  }

  static EvalConstant create(CelByteString byteString) {
    if (byteString.isEmpty()) {
      return EMPTY_BYTES;
    }
    return new EvalConstant(byteString);
  }

  private EvalConstant(Object constant) {
    this.constant = constant;
  }
}
