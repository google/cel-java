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

import static dev.cel.runtime.standard.ArithmeticHelpers.getArithmeticErrorCode;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;
import java.util.List;

/** Standard function for the addition (+) operator. */
public final class AddOperator extends CelStandardFunction {
  private static final AddOperator ALL_OVERLOADS = create(AddOverload.values());

  public static AddOperator create() {
    return ALL_OVERLOADS;
  }

  public static AddOperator create(AddOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static AddOperator create(Iterable<AddOverload> overloads) {
    return new AddOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum AddOverload implements CelStandardOverload {
    ADD_INT64(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "add_int64",
                Long.class,
                Long.class,
                (Long x, Long y) -> {
                  try {
                    return RuntimeHelpers.int64Add(x, y, celOptions);
                  } catch (ArithmeticException e) {
                    throw new CelRuntimeException(e, getArithmeticErrorCode(e));
                  }
                })),
    ADD_UINT64(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "add_uint64",
                UnsignedLong.class,
                UnsignedLong.class,
                (UnsignedLong x, UnsignedLong y) -> {
                  try {
                    return RuntimeHelpers.uint64Add(x, y);
                  } catch (ArithmeticException e) {
                    throw new CelRuntimeException(e, getArithmeticErrorCode(e));
                  }
                });
          } else {
            return CelFunctionBinding.from(
                "add_uint64",
                Long.class,
                Long.class,
                (Long x, Long y) -> {
                  try {
                    return RuntimeHelpers.uint64Add(x, y, celOptions);
                  } catch (ArithmeticException e) {
                    throw new CelRuntimeException(e, getArithmeticErrorCode(e));
                  }
                });
          }
        }),
    ADD_BYTES(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "add_bytes", ByteString.class, ByteString.class, ByteString::concat)),
    ADD_DOUBLE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("add_double", Double.class, Double.class, Double::sum)),
    ADD_DURATION_DURATION(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "add_duration_duration", Duration.class, Duration.class, ProtoTimeUtils::add)),
    ADD_TIMESTAMP_DURATION(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "add_timestamp_duration", Timestamp.class, Duration.class, ProtoTimeUtils::add)),
    ADD_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "add_string", String.class, String.class, (String x, String y) -> x + y)),
    ADD_DURATION_TIMESTAMP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "add_duration_timestamp",
                Duration.class,
                Timestamp.class,
                (Duration x, Timestamp y) -> ProtoTimeUtils.add(y, x))),
    @SuppressWarnings({"unchecked"})
    ADD_LIST(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("add_list", List.class, List.class, RuntimeHelpers::concat));

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    AddOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private AddOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
