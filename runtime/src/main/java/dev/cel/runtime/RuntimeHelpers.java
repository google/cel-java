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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLong;
import com.google.common.primitives.UnsignedLongs;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import com.google.re2j.Pattern;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.Converter;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoAdapter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.threeten.extra.AmountFormats;

/**
 * Helper methods for common CEL related routines.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class RuntimeHelpers {

  // Maximum and minimum range supported by protobuf Duration values.
  private static final java.time.Duration DURATION_MAX = java.time.Duration.ofDays(3652500);
  private static final java.time.Duration DURATION_MIN = DURATION_MAX.negated();

  private static final DynamicProto DYNAMIC_PROTO_INSTANCE = DynamicProto.newBuilder().build();

  // Functions
  // =========

  /** Convert a string to a Duration. */
  public static Duration createDurationFromString(String d) {
    try {
      java.time.Duration dv = AmountFormats.parseUnitBasedDuration(d);
      // Ensure that the duration value can be adequately represented within a protobuf.Duration.
      checkArgument(
          dv.compareTo(DURATION_MAX) <= 0 && dv.compareTo(DURATION_MIN) >= 0,
          "invalid duration range");
      return Duration.newBuilder().setSeconds(dv.getSeconds()).setNanos(dv.getNano()).build();
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("invalid duration format", e);
    }
  }

  /** Match a string against a regular expression. */
  public static boolean matches(String string, String regexp) {
    return matches(
        string, regexp, CelOptions.newBuilder().disableCelStandardEquality(false).build());
  }

  public static boolean matches(String string, String regexp, CelOptions celOptions) {
    if (!celOptions.enableRegexPartialMatch()) {
      // Uses re2 for consistency across languages.
      return Pattern.matches(regexp, string);
    }
    // Return an unanchored match for the presence of the regexp anywher in the string.
    return Pattern.compile(regexp).matcher(string).find();
  }

  /** Create a compiled pattern for the given regular expression. */
  public static Pattern compilePattern(String regexp) {
    return Pattern.compile(regexp);
  }

  /** Concatenates two lists into a new list. */
  public static <E> List<E> concat(List<E> first, List<E> second) {
    // TODO: return a view instead of an actual copy.
    List<E> result = new ArrayList<>(first.size() + second.size());
    result.addAll(first);
    result.addAll(second);
    return result;
  }

  // Collections
  // ===========

  /** Bound-checked indexing of lists. */
  public static <A> A indexList(List<A> list, Number index) {
    if (index instanceof Double) {
      return doubleToLongLossless(index.doubleValue())
          .map(v -> indexList(list, v))
          .orElseThrow(
              () ->
                  new CelRuntimeException(
                      new IndexOutOfBoundsException("Index out of bounds: " + index.doubleValue()),
                      CelErrorCode.INDEX_OUT_OF_BOUNDS));
    }
    int castIndex = Ints.checkedCast(index.longValue());
    if (castIndex < 0 || castIndex >= list.size()) {
      throw new CelRuntimeException(
          new IndexOutOfBoundsException("Index out of bounds: " + castIndex),
          CelErrorCode.INDEX_OUT_OF_BOUNDS);
    }
    return list.get(castIndex);
  }

  // Integer Arithmetic
  // ==================
  //
  // CEL requires exceptions to be thrown when int arithmetic exceeds the represented range.

  public static long int64Add(long x, long y, CelOptions celOptions) {
    if (celOptions.errorOnIntWrap()) {
      return Math.addExact(x, y);
    }
    return x + y;
  }

  public static long int64Divide(long x, long y, CelOptions celOptions) {
    if (celOptions.errorOnIntWrap() && x == Long.MIN_VALUE && y == -1) {
      throw new ArithmeticException("most negative number wraps");
    }
    return x / y;
  }

  public static long int64Multiply(long x, long y, CelOptions celOptions) {
    if (celOptions.errorOnIntWrap()) {
      return Math.multiplyExact(x, y);
    }
    return x * y;
  }

  public static long int64Negate(long x, CelOptions celOptions) {
    if (celOptions.errorOnIntWrap()) {
      return Math.negateExact(x);
    }
    return -x;
  }

  public static long int64Subtract(long x, long y, CelOptions celOptions) {
    if (celOptions.errorOnIntWrap()) {
      return Math.subtractExact(x, y);
    }
    return x - y;
  }

  // Unsigned Arithmetic
  // ===================

  // Some arithmetic for unsigned longs cannot be handled via signed longs.
  // See
  // http://stackoverflow.com/questions/14063599/why-are-signed-and-unsigned-multiplication-different-instructions-on-x86-64

  // We need to use UnsignedLong.fromLongBits() rather than UnsignedLong.valueOf(). The later only
  // works for signed long values that are greater than or equal to 0. The former reinterprets the
  // long as unsigned, using the bits as is.

  public static long uint64Add(long x, long y, CelOptions celOptions) {
    if (celOptions.errorOnIntWrap()) {
      if (x < 0 && y < 0) {
        // Both numbers are in the upper half of the range, so it must overflow.
        throw new ArithmeticException("range overflow on unsigned addition");
      }
      long z = x + y;
      if ((x < 0 || y < 0) && z >= 0) {
        // Only one number is in the upper half of the range. It overflows if the result
        // is not in the upper half.
        throw new ArithmeticException("range overflow on unsigned addition");
      }
      return z;
    }
    return x + y;
  }

  public static UnsignedLong uint64Add(UnsignedLong x, UnsignedLong y) {
    if (x.compareTo(UnsignedLong.MAX_VALUE.minus(y)) > 0) {
      throw new ArithmeticException("range overflow on unsigned addition");
    }
    return x.plus(y);
  }

  public static int uint64CompareTo(long x, long y, CelOptions celOptions) {
    return celOptions.enableUnsignedComparisonAndArithmeticIsUnsigned()
        ? UnsignedLongs.compare(x, y)
        : UnsignedLong.valueOf(x).compareTo(UnsignedLong.valueOf(y));
  }

  public static int uint64CompareTo(long x, long y) {
    // Features is set to empty, as this class is public and the build visibility is public.
    // Existing callers expect legacy behavior.
    return uint64CompareTo(x, y, CelOptions.LEGACY);
  }

  public static int uint64CompareTo(UnsignedLong x, UnsignedLong y) {
    return x.compareTo(y);
  }

  public static long uint64Divide(long x, long y, CelOptions celOptions) {
    try {
      return celOptions.enableUnsignedComparisonAndArithmeticIsUnsigned()
          ? UnsignedLongs.divide(x, y)
          : UnsignedLong.valueOf(x).dividedBy(UnsignedLong.valueOf(y)).longValue();
    } catch (ArithmeticException e) {
      throw new CelRuntimeException(e, CelErrorCode.DIVIDE_BY_ZERO);
    }
  }

  public static long uint64Divide(long x, long y) {
    // Features is set to empty, as this class is public and the build visibility is public.
    // Existing callers expect legacy behavior.
    return uint64Divide(x, y, CelOptions.LEGACY);
  }

  public static UnsignedLong uint64Divide(UnsignedLong x, UnsignedLong y) {
    if (y.equals(UnsignedLong.ZERO)) {
      throw new CelRuntimeException(
          new ArithmeticException("/ by zero"), CelErrorCode.DIVIDE_BY_ZERO);
    }
    return x.dividedBy(y);
  }

  public static long uint64Mod(long x, long y, CelOptions celOptions) {
    try {
      return celOptions.enableUnsignedComparisonAndArithmeticIsUnsigned()
          ? UnsignedLongs.remainder(x, y)
          : UnsignedLong.valueOf(x).mod(UnsignedLong.valueOf(y)).longValue();
    } catch (ArithmeticException e) {
      throw new CelRuntimeException(e, CelErrorCode.DIVIDE_BY_ZERO);
    }
  }

  public static UnsignedLong uint64Mod(UnsignedLong x, UnsignedLong y) {
    if (y.equals(UnsignedLong.ZERO)) {
      throw new CelRuntimeException(
          new ArithmeticException("/ by zero"), CelErrorCode.DIVIDE_BY_ZERO);
    }
    return x.mod(y);
  }

  public static long uint64Mod(long x, long y) {
    // Features is set to empty, as this class is public and the build visibility is public.
    // Existing callers expect legacy behavior.
    return uint64Mod(x, y, CelOptions.LEGACY);
  }

  public static long uint64Multiply(long x, long y, CelOptions celOptions) {
    long z =
        celOptions.enableUnsignedComparisonAndArithmeticIsUnsigned()
            ? x * y
            : UnsignedLong.valueOf(x).times(UnsignedLong.valueOf(y)).longValue();
    if (celOptions.errorOnIntWrap() && y != 0 && Long.divideUnsigned(z, y) != x) {
      throw new ArithmeticException("multiply out of unsigned integer range");
    }
    return z;
  }

  public static long uint64Multiply(long x, long y) {
    // Features is set to empty, as this class is public and the build visibility is public.
    // Existing callers expect legacy behavior.
    return uint64Multiply(x, y, CelOptions.LEGACY);
  }

  public static UnsignedLong uint64Multiply(UnsignedLong x, UnsignedLong y) {
    if (!y.equals(UnsignedLong.ZERO) && x.compareTo(UnsignedLong.MAX_VALUE.dividedBy(y)) > 0) {
      throw new ArithmeticException("multiply out of unsigned integer range");
    }
    return x.times(y);
  }

  public static long uint64Subtract(long x, long y, CelOptions celOptions) {
    if (celOptions.errorOnIntWrap()) {
      // Throw an overflow error if x < y, as unsigned longs. This happens if y has its high
      // bit set and x does not, or if they have the same high bit and x < y as signed longs.
      if ((x < 0 && y < 0 && x < y) || (x >= 0 && y >= 0 && x < y) || (x >= 0 && y < 0)) {
        throw new ArithmeticException("unsigned subtraction underflow");
      }
      // fallthrough
    }
    return x - y;
  }

  public static UnsignedLong uint64Subtract(UnsignedLong x, UnsignedLong y) {
    // Throw an overflow error if x < y, as unsigned longs. This happens if y has its high
    // bit set and x does not, or if they have the same high bit and x < y as signed longs.
    if (x.compareTo(y) < 0) {
      throw new ArithmeticException("unsigned subtraction underflow");
    }
    return x.minus(y);
  }

  // Object equality
  // ===================

  // Proto Type Adaption
  // ===================

  // CEL unifies int32, int64, and enum, and float and double. Values selected or assigned to
  // protobuf fields need to be adapted to CEL's simpler type system. For collections, we
  // want to avoid to do this conversion eagerly, so we create views on the underlying data.
  // The below code is the extensive boilerplate to do so.

  public static <A> Converter<A, A> identity() {
    return (A value) -> value;
  }

  public static final Converter<Integer, Long> INT32_TO_INT64 = Integer::longValue;

  public static final Converter<Integer, Long> UINT32_TO_UINT64 = UnsignedInts::toLong;

  public static final Converter<Float, Double> FLOAT_TO_DOUBLE = Float::doubleValue;

  public static final Converter<Long, Integer> INT64_TO_INT32 = Ints::checkedCast;

  public static final Converter<Double, Float> DOUBLE_TO_FLOAT = Double::floatValue;

  /** Adapts a plain old Java object into a CEL value. */
  public static Object adaptValue(DynamicProto dynamicProto, Object value, CelOptions celOptions) {
    if (value == null) {
      return NullValue.NULL_VALUE;
    }
    if (value instanceof Number) {
      return maybeAdaptPrimitive(value);
    }
    if (value instanceof MessageOrBuilder) {
      return adaptProtoToValue(dynamicProto, (MessageOrBuilder) value, celOptions);
    }
    return value;
  }

  /** Adapts a {@code Number} value to its appropriate CEL type. */
  public static Object maybeAdaptPrimitive(Object value) {
    if (value instanceof Optional<?>) {
      Optional<?> optionalVal = (Optional<?>) value;
      if (!optionalVal.isPresent()) {
        return optionalVal;
      }
      return Optional.of(maybeAdaptPrimitive(optionalVal.get()));
    }
    if (value instanceof Float) {
      return FLOAT_TO_DOUBLE.convert((Float) value);
    }
    if (value instanceof Integer) {
      return INT32_TO_INT64.convert((Integer) value);
    }
    return value;
  }

  static Object adaptProtoToValue(MessageOrBuilder obj, CelOptions celOptions) {
    return adaptProtoToValue(DYNAMIC_PROTO_INSTANCE, obj, celOptions);
  }

  /**
   * Adapts a {@code protobuf.Message} to a plain old Java object.
   *
   * <p>Well-known protobuf types (wrappers, JSON types) are unwrapped to Java native object
   * representations.
   *
   * <p>If the incoming {@code obj} is of type {@code google.protobuf.Any} the object is unpacked
   * and the proto within is passed to the {@code adaptProtoToValue} method again to ensure the
   * message contained within the Any is properly unwrapped if it is a well-known protobuf type.
   */
  public static Object adaptProtoToValue(
      DynamicProto dynamicProto, MessageOrBuilder obj, CelOptions celOptions) {
    ProtoAdapter protoAdapter = new ProtoAdapter(dynamicProto, celOptions.enableUnsignedLongs());
    if (obj instanceof Message) {
      return protoAdapter.adaptProtoToValue(obj);
    }
    if (obj instanceof Message.Builder) {
      return protoAdapter.adaptProtoToValue(((Message.Builder) obj).build());
    }
    return obj;
  }

  public static Optional<UnsignedLong> doubleToUnsignedChecked(double v) {
    // getExponent of NaN or Infinite will return a Double.MAX_EXPONENT + 1 (or 128)
    if (v < 0.0 || Math.getExponent(v) >= 64) {
      return Optional.empty();
    }
    if (v >= Math.scalb(1.0, 63)) {
      // If in the upper range 2^63 <= arg < 2^64, move into the representable range for
      // signed long, mod 2^64. Note that we cannot have a fractional part since
      // there aren't enough mantissa bits, so we don't need to worry about rounding.
      v -= Math.scalb(1.0, 64);
    }
    return Optional.of(UnsignedLong.fromLongBits((long) v));
  }

  public static Optional<Long> doubleToLongChecked(double v) {
    // getExponent of NaN or Infinite values will return a Double.MAX_EXPONENT + 1 (or 128)
    int exp = Math.getExponent(v);
    if (exp >= 63 && v != Math.scalb(-1.0, 63)) {
      return Optional.empty();
    }
    return Optional.of((long) v);
  }

  public static Optional<Long> doubleToLongLossless(Number v) {
    Optional<Long> conv = doubleToLongChecked(v.doubleValue());
    return conv.map(l -> l.doubleValue() == v.doubleValue() ? l : null);
  }

  private RuntimeHelpers() {}
}
