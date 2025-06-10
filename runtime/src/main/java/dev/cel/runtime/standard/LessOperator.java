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

package dev.cel.runtime.standard;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;

/** Standard function for the less (<) operator. */
public final class LessOperator extends CelStandardFunction {

  public static LessOperator create(LessOperator.LessOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static LessOperator create(Iterable<LessOperator.LessOverload> overloads) {
    return new LessOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum LessOverload implements CelStandardOverload {
    LESS_BOOL(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_bool", Boolean.class, Boolean.class, (Boolean x, Boolean y) -> !x && y)),
    LESS_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_int64", Long.class, Long.class, (Long x, Long y) -> x < y)),
    LESS_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "less_uint64",
                UnsignedLong.class,
                UnsignedLong.class,
                (UnsignedLong x, UnsignedLong y) -> RuntimeHelpers.uint64CompareTo(x, y) < 0);
          } else {
            return CelFunctionBinding.from(
                "less_uint64",
                Long.class,
                Long.class,
                (Long x, Long y) -> RuntimeHelpers.uint64CompareTo(x, y, celOptions) < 0);
          }
        }),
    LESS_BYTES(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_bytes",
                ByteString.class,
                ByteString.class,
                (ByteString x, ByteString y) ->
                    ByteString.unsignedLexicographicalComparator().compare(x, y) < 0)),
    LESS_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_double", Double.class, Double.class, (Double x, Double y) -> x < y)),
    LESS_DOUBLE_UINT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_double_uint64",
                Double.class,
                UnsignedLong.class,
                (Double x, UnsignedLong y) -> ComparisonFunctions.compareDoubleUint(x, y) == -1)),
    LESS_INT64_UINT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_int64_uint64",
                Long.class,
                UnsignedLong.class,
                (Long x, UnsignedLong y) -> ComparisonFunctions.compareIntUint(x, y) == -1)),
    LESS_UINT64_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_uint64_int64",
                UnsignedLong.class,
                Long.class,
                (UnsignedLong x, Long y) -> ComparisonFunctions.compareUintInt(x, y) == -1)),
    LESS_INT64_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_int64_double",
                Long.class,
                Double.class,
                (Long x, Double y) -> ComparisonFunctions.compareIntDouble(x, y) == -1)),
    LESS_DOUBLE_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_double_int64",
                Double.class,
                Long.class,
                (Double x, Long y) -> ComparisonFunctions.compareDoubleInt(x, y) == -1)),
    LESS_UINT64_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_uint64_double",
                UnsignedLong.class,
                Double.class,
                (UnsignedLong x, Double y) -> ComparisonFunctions.compareUintDouble(x, y) == -1)),
    LESS_DURATION(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_duration",
                Duration.class,
                Duration.class,
                (Duration x, Duration y) -> ProtoTimeUtils.compare(x, y) < 0)),
    LESS_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_string",
                String.class,
                String.class,
                (String x, String y) -> x.compareTo(y) < 0)),
    LESS_TIMESTAMP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "less_timestamp",
                Timestamp.class,
                Timestamp.class,
                (Timestamp x, Timestamp y) -> ProtoTimeUtils.compare(x, y) < 0)),
    ;

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    LessOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private LessOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
