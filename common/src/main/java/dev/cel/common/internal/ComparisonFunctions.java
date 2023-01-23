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

package dev.cel.common.internal;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.annotations.Internal;

/**
 * The {@code ComparisonFunctions} methods provide safe cross-type comparisons between {@code long},
 * {@code double}, and {@link UnsignedLong} values.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@CheckReturnValue
@Internal
public final class ComparisonFunctions {

  private static final UnsignedLong UINT_TO_INT_MAX = UnsignedLong.valueOf(Long.MAX_VALUE);
  private static final double DOUBLE_TO_INT_MAX = (double) Long.MAX_VALUE;
  private static final double DOUBLE_TO_INT_MIN = (double) Long.MIN_VALUE;
  private static final double DOUBLE_TO_UINT_MAX = UnsignedLong.MAX_VALUE.doubleValue();

  public static int compareDoubleInt(double d, long l) {
    if (d < DOUBLE_TO_INT_MIN) {
      return -1;
    }
    if (d > DOUBLE_TO_INT_MAX) {
      return 1;
    }
    return Double.compare(d, (double) l);
  }

  public static int compareIntDouble(long l, double d) {
    return -compareDoubleInt(d, l);
  }

  public static int compareDoubleUint(double d, UnsignedLong ul) {
    if (d < 0.0) {
      return -1;
    }
    if (d > DOUBLE_TO_UINT_MAX) {
      return 1;
    }
    return Double.compare(d, ul.doubleValue());
  }

  public static int compareUintDouble(UnsignedLong ul, double d) {
    return -compareDoubleUint(d, ul);
  }

  public static int compareIntUint(long l, UnsignedLong ul) {
    if (l < 0 || ul.compareTo(UINT_TO_INT_MAX) >= 0) {
      return -1;
    }
    return Long.compare(l, ul.longValue());
  }

  public static int compareUintInt(UnsignedLong ul, long l) {
    return -compareIntUint(l, ul);
  }

  /**
   * Compare two numeric values of any type (double, int, uint) for equality.
   *
   * <p>Floating point values are follow IEEE 754 standard for NaN comparisons.
   */
  public static boolean numericEquals(Number x, Number y) {
    if (x instanceof Double) {
      if (y instanceof Double) {
        return !(Double.isNaN((Double) x) || Double.isNaN((Double) y)) && x.equals(y);
      }
      if (y instanceof Long) {
        return compareDoubleInt((Double) x, (Long) y) == 0;
      }
      if (y instanceof UnsignedLong) {
        return compareDoubleUint((Double) x, (UnsignedLong) y) == 0;
      }
    }
    if (x instanceof Long) {
      if (y instanceof Long) {
        return x.equals(y);
      }
      if (y instanceof Double) {
        return compareIntDouble((Long) x, (Double) y) == 0;
      }
      if (y instanceof UnsignedLong) {
        return compareIntUint((Long) x, (UnsignedLong) y) == 0;
      }
    }
    if (x instanceof UnsignedLong) {
      if (y instanceof UnsignedLong) {
        return x.equals(y);
      }
      if (y instanceof Double) {
        return compareUintDouble((UnsignedLong) x, (Double) y) == 0;
      }
      if (y instanceof Long) {
        return compareUintInt((UnsignedLong) x, (Long) y) == 0;
      }
    }
    return false;
  }

  private ComparisonFunctions() {}
}
