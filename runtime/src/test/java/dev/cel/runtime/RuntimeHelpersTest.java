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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RuntimeHelpersTest {
  private static final DynamicProto DYNAMIC_PROTO =
      DynamicProto.create(DefaultMessageFactory.INSTANCE);

  @Test
  public void createDurationFromString() throws Exception {
    assertThat(RuntimeHelpers.createDurationFromString("15.11s"))
        .isEqualTo(Duration.newBuilder().setSeconds(15).setNanos(110000000).build());
  }

  @Test
  public void createDurationFromString_outOfRange() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> RuntimeHelpers.createDurationFromString("-320000000000s"));
  }

  @Test
  public void int64Add() throws Exception {
    assertThat(RuntimeHelpers.int64Add(1, 1, CelOptions.LEGACY)).isEqualTo(2);
    assertThat(RuntimeHelpers.int64Add(2, 2, CelOptions.DEFAULT)).isEqualTo(4);
    assertThat(RuntimeHelpers.int64Add(1, Long.MAX_VALUE, CelOptions.LEGACY))
        .isEqualTo(Long.MIN_VALUE);
    assertThat(RuntimeHelpers.int64Add(-1, Long.MIN_VALUE, CelOptions.LEGACY))
        .isEqualTo(Long.MAX_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.int64Add(1, Long.MAX_VALUE, CelOptions.DEFAULT));
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.int64Add(-1, Long.MIN_VALUE, CelOptions.DEFAULT));
  }

  @Test
  public void int64Divide() throws Exception {
    assertThat(RuntimeHelpers.int64Divide(-44, 11, CelOptions.LEGACY)).isEqualTo(-4);
    assertThat(RuntimeHelpers.int64Divide(-44, 11, CelOptions.DEFAULT)).isEqualTo(-4);
    assertThat(RuntimeHelpers.int64Divide(Long.MIN_VALUE, -1, CelOptions.LEGACY))
        .isEqualTo(Long.MIN_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.int64Divide(Long.MIN_VALUE, -1, CelOptions.DEFAULT));
  }

  @Test
  public void int64Multiply() throws Exception {
    assertThat(RuntimeHelpers.int64Multiply(2, 3, CelOptions.LEGACY)).isEqualTo(6);
    assertThat(RuntimeHelpers.int64Multiply(2, 3, CelOptions.DEFAULT)).isEqualTo(6);
    assertThat(RuntimeHelpers.int64Multiply(Long.MIN_VALUE, -1, CelOptions.LEGACY))
        .isEqualTo(Long.MIN_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.int64Multiply(Long.MIN_VALUE, -1, CelOptions.DEFAULT));
  }

  @Test
  public void int64Negate() throws Exception {
    assertThat(RuntimeHelpers.int64Negate(7, CelOptions.LEGACY)).isEqualTo(-7);
    assertThat(RuntimeHelpers.int64Negate(7, CelOptions.DEFAULT)).isEqualTo(-7);
    assertThat(RuntimeHelpers.int64Negate(Long.MIN_VALUE, CelOptions.LEGACY))
        .isEqualTo(Long.MIN_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.int64Negate(Long.MIN_VALUE, CelOptions.DEFAULT));
  }

  @Test
  public void int64Subtract() throws Exception {
    assertThat(RuntimeHelpers.int64Subtract(50, 100, CelOptions.LEGACY)).isEqualTo(-50);
    assertThat(RuntimeHelpers.int64Subtract(50, 100, CelOptions.DEFAULT)).isEqualTo(-50);
    assertThat(RuntimeHelpers.int64Subtract(Long.MIN_VALUE, 1, CelOptions.LEGACY))
        .isEqualTo(Long.MAX_VALUE);
    assertThat(RuntimeHelpers.int64Subtract(Long.MAX_VALUE, -1, CelOptions.LEGACY))
        .isEqualTo(Long.MIN_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.int64Subtract(Long.MIN_VALUE, 1, CelOptions.DEFAULT));
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.int64Subtract(Long.MAX_VALUE, -1, CelOptions.DEFAULT));
  }

  @Test
  public void uint64CompareTo_unsignedLongs() {
    assertThat(RuntimeHelpers.uint64CompareTo(UnsignedLong.ONE, UnsignedLong.ZERO)).isEqualTo(1);
    assertThat(RuntimeHelpers.uint64CompareTo(UnsignedLong.ZERO, UnsignedLong.ONE)).isEqualTo(-1);
    assertThat(RuntimeHelpers.uint64CompareTo(UnsignedLong.ONE, UnsignedLong.ONE)).isEqualTo(0);
    assertThat(
            RuntimeHelpers.uint64CompareTo(
                UnsignedLong.valueOf(Long.MAX_VALUE), UnsignedLong.MAX_VALUE))
        .isEqualTo(-1);
  }

  @Test
  public void uint64CompareTo_throwsWhenNegativeOrGreaterThanSignedLongMax() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64CompareTo(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64CompareTo(0, -1));
  }

  @Test
  public void uint64CompareTo_unsignedComparisonAndArithmeticIsUnsigned() throws Exception {
    // In twos complement, -1 is represented by all bits being set. This is equivalent to the
    // maximum value when unsigned.
    assertThat(RuntimeHelpers.uint64CompareTo(-1, 0, CelOptions.DEFAULT)).isGreaterThan(0);
    assertThat(RuntimeHelpers.uint64CompareTo(0, -1, CelOptions.DEFAULT)).isLessThan(0);
  }

  @Test
  public void uint64Add_signedLongs() throws Exception {
    assertThat(RuntimeHelpers.uint64Add(4, 4, CelOptions.LEGACY)).isEqualTo(8);
    assertThat(RuntimeHelpers.uint64Add(4, 4, CelOptions.DEFAULT)).isEqualTo(8);
    assertThat(RuntimeHelpers.uint64Add(-1, 1, CelOptions.LEGACY)).isEqualTo(0);
    assertThrows(
        ArithmeticException.class, () -> RuntimeHelpers.uint64Add(-1, 1, CelOptions.DEFAULT));
  }

  @Test
  public void uint64Add_unsignedLongs() throws Exception {
    assertThat(RuntimeHelpers.uint64Add(UnsignedLong.valueOf(4), UnsignedLong.valueOf(4)))
        .isEqualTo(UnsignedLong.valueOf(8));
    assertThat(
            RuntimeHelpers.uint64Add(
                UnsignedLong.MAX_VALUE.minus(UnsignedLong.ONE), UnsignedLong.ONE))
        .isEqualTo(UnsignedLong.MAX_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.uint64Add(UnsignedLong.MAX_VALUE, UnsignedLong.ONE));
  }

  @Test
  public void uint64Multiply_signedLongs() throws Exception {
    assertThat(RuntimeHelpers.uint64Multiply(32, 2, CelOptions.LEGACY)).isEqualTo(64);
    assertThat(RuntimeHelpers.uint64Multiply(32, 2, CelOptions.DEFAULT)).isEqualTo(64);
    assertThat(
            RuntimeHelpers.uint64Multiply(
                Long.MIN_VALUE,
                2,
                CelOptions.newBuilder()
                    .enableUnsignedComparisonAndArithmeticIsUnsigned(true)
                    .build()))
        .isEqualTo(0);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.uint64Multiply(Long.MIN_VALUE, 2, CelOptions.DEFAULT));
  }

  @Test
  public void uint64Multiply_unsignedLongs() throws Exception {
    assertThat(RuntimeHelpers.uint64Multiply(UnsignedLong.valueOf(32), UnsignedLong.valueOf(2)))
        .isEqualTo(UnsignedLong.valueOf(64));
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.uint64Multiply(UnsignedLong.MAX_VALUE, UnsignedLong.valueOf(2)));
  }

  @Test
  public void uint64Multiply_throwsWhenNegativeOrGreaterThanSignedLongMax() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64Multiply(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64Multiply(0, -1));
  }

  @Test
  public void uint64Multiply_unsignedComparisonAndArithmeticIsUnsigned() throws Exception {
    // In twos complement, -1 is represented by all bits being set. This is equivalent to the
    // maximum value when unsigned.
    assertThat(RuntimeHelpers.uint64Multiply(-1, 0, CelOptions.DEFAULT)).isEqualTo(0);
    assertThat(RuntimeHelpers.uint64Multiply(0, -1, CelOptions.DEFAULT)).isEqualTo(0);
  }

  @Test
  public void uint64Divide_unsignedLongs() {
    assertThat(RuntimeHelpers.uint64Divide(UnsignedLong.ZERO, UnsignedLong.ONE))
        .isEqualTo(UnsignedLong.ZERO);
    assertThat(RuntimeHelpers.uint64Divide(UnsignedLong.MAX_VALUE, UnsignedLong.MAX_VALUE))
        .isEqualTo(UnsignedLong.ONE);
    assertThrows(
        CelRuntimeException.class,
        () -> RuntimeHelpers.uint64Divide(UnsignedLong.MAX_VALUE, UnsignedLong.ZERO));
  }

  @Test
  public void uint64Divide_throwsWhenNegativeOrGreaterThanSignedLongMax() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64Divide(0, -1));
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64Divide(-1, -1));
  }

  @Test
  public void uint64Divide_unsignedComparisonAndArithmeticIsUnsigned() throws Exception {
    // In twos complement, -1 is represented by all bits being set. This is equivalent to the
    // maximum value when unsigned.
    assertThat(RuntimeHelpers.uint64Divide(0, -1, CelOptions.DEFAULT)).isEqualTo(0);
    assertThat(RuntimeHelpers.uint64Divide(-1, -1, CelOptions.DEFAULT)).isEqualTo(1);
  }

  @Test
  public void uint64Mod_unsignedLongs() throws Exception {
    assertThat(RuntimeHelpers.uint64Mod(UnsignedLong.ONE, UnsignedLong.valueOf(2)))
        .isEqualTo(UnsignedLong.ONE);
    assertThat(RuntimeHelpers.uint64Mod(UnsignedLong.ONE, UnsignedLong.ONE))
        .isEqualTo(UnsignedLong.ZERO);
    assertThrows(
        CelRuntimeException.class,
        () -> RuntimeHelpers.uint64Mod(UnsignedLong.ONE, UnsignedLong.ZERO));
  }

  @Test
  public void uint64Mod_throwsWhenNegativeOrGreaterThanSignedLongMax() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64Mod(0, -1));
    assertThrows(IllegalArgumentException.class, () -> RuntimeHelpers.uint64Mod(-1, -1));
  }

  @Test
  public void uint64Mod_unsignedComparisonAndArithmeticIsUnsigned() throws Exception {
    // In twos complement, -1 is represented by all bits being set. This is equivalent to the
    // maximum value when unsigned.
    assertThat(RuntimeHelpers.uint64Mod(0, -1, CelOptions.DEFAULT)).isEqualTo(0);
    assertThat(RuntimeHelpers.uint64Mod(-1, -1, CelOptions.DEFAULT)).isEqualTo(0);
  }

  @Test
  public void uint64Subtract_signedLongs() throws Exception {
    assertThat(RuntimeHelpers.uint64Subtract(-1, 2, CelOptions.LEGACY)).isEqualTo(-3);
    assertThat(RuntimeHelpers.uint64Subtract(-1, 2, CelOptions.DEFAULT)).isEqualTo(-3);
    assertThat(RuntimeHelpers.uint64Subtract(0, 1, CelOptions.LEGACY)).isEqualTo(-1);
    assertThrows(
        ArithmeticException.class, () -> RuntimeHelpers.uint64Subtract(0, 1, CelOptions.DEFAULT));
    assertThrows(
        ArithmeticException.class, () -> RuntimeHelpers.uint64Subtract(-3, -1, CelOptions.DEFAULT));
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.uint64Subtract(55, -40, CelOptions.DEFAULT));
  }

  @Test
  public void uint64Subtract_unsignedLongs() throws Exception {
    assertThat(RuntimeHelpers.uint64Subtract(UnsignedLong.ONE, UnsignedLong.ONE))
        .isEqualTo(UnsignedLong.ZERO);
    assertThat(RuntimeHelpers.uint64Subtract(UnsignedLong.valueOf(3), UnsignedLong.valueOf(2)))
        .isEqualTo(UnsignedLong.ONE);
    assertThrows(
        ArithmeticException.class,
        () -> RuntimeHelpers.uint64Subtract(UnsignedLong.ONE, UnsignedLong.valueOf(2)));
  }

  @Test
  public void maybeAdaptPrimitive_optionalValues() {
    // Optional values containing int/float are adapted into long/double
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of(5))).isEqualTo(Optional.of(5L));
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of(5.5f))).isEqualTo(Optional.of(5.5d));
    // Optional values for the rest of the primitive types are not adapted
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of(5L))).isEqualTo(Optional.of(5L));
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of(5.5d))).isEqualTo(Optional.of(5.5d));
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of(true))).isEqualTo(Optional.of(true));
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of(false)))
        .isEqualTo(Optional.of(false));
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of("test")))
        .isEqualTo(Optional.of("test"));
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(Optional.of(UnsignedLong.valueOf(5))))
        .isEqualTo(Optional.of(UnsignedLong.valueOf(5)));
    assertThat(RuntimeHelpers.maybeAdaptPrimitive(BytesValue.of(ByteString.copyFromUtf8("test"))))
        .isEqualTo(BytesValue.of(ByteString.copyFromUtf8("test")));
  }

  @Test
  public void adaptProtoToValue_wrapperValues() throws Exception {
    CelOptions celOptions = CelOptions.LEGACY;
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, BoolValue.of(true), celOptions))
        .isEqualTo(true);
    assertThat(
            RuntimeHelpers.adaptProtoToValue(
                DYNAMIC_PROTO, BytesValue.of(ByteString.EMPTY), celOptions))
        .isEqualTo(ByteString.EMPTY);
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, DoubleValue.of(1.5d), celOptions))
        .isEqualTo(1.5d);
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, FloatValue.of(1.5f), celOptions))
        .isEqualTo(1.5d);
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, Int32Value.of(12), celOptions))
        .isEqualTo(12L);
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, Int64Value.of(-12L), celOptions))
        .isEqualTo(-12L);
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, UInt32Value.of(123), celOptions))
        .isEqualTo(123L);
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, UInt64Value.of(1234L), celOptions))
        .isEqualTo(1234L);
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, StringValue.of("hello"), celOptions))
        .isEqualTo("hello");

    assertThat(
            RuntimeHelpers.adaptProtoToValue(
                DYNAMIC_PROTO,
                UInt32Value.of(123),
                CelOptions.newBuilder().enableUnsignedLongs(true).build()))
        .isEqualTo(UnsignedLong.valueOf(123L));
    assertThat(
            RuntimeHelpers.adaptProtoToValue(
                DYNAMIC_PROTO,
                UInt64Value.of(1234L),
                CelOptions.newBuilder().enableUnsignedLongs(true).build()))
        .isEqualTo(UnsignedLong.valueOf(1234L));
  }

  @Test
  public void adaptProtoToValue_jsonValues() throws Exception {
    assertThat(
            RuntimeHelpers.adaptProtoToValue(
                DYNAMIC_PROTO,
                Value.newBuilder().setStringValue("json").build(),
                CelOptions.LEGACY))
        .isEqualTo("json");

    assertThat(
            RuntimeHelpers.adaptProtoToValue(
                DYNAMIC_PROTO,
                Value.newBuilder()
                    .setListValue(
                        ListValue.newBuilder()
                            .addValues(Value.newBuilder().setNumberValue(1.2d).build()))
                    .build(),
                CelOptions.LEGACY))
        .isEqualTo(ImmutableList.of(1.2d));

    Map<String, Object> mp = new HashMap<>();
    mp.put("list_value", ImmutableList.of(false, NullValue.NULL_VALUE));
    assertThat(
            RuntimeHelpers.adaptProtoToValue(
                DYNAMIC_PROTO,
                Struct.newBuilder()
                    .putFields(
                        "list_value",
                        Value.newBuilder()
                            .setListValue(
                                ListValue.newBuilder()
                                    .addValues(Value.newBuilder().setBoolValue(false))
                                    .addValues(
                                        Value.newBuilder().setNullValue(NullValue.NULL_VALUE)))
                            .build())
                    .build(),
                CelOptions.LEGACY))
        .isEqualTo(mp);
  }

  @Test
  public void adaptProtoToValue_anyValues() throws Exception {
    Map<String, Object> mp = new HashMap<>();
    Struct jsonValue =
        Struct.newBuilder()
            .putFields(
                "list_value",
                Value.newBuilder()
                    .setListValue(
                        ListValue.newBuilder()
                            .addValues(Value.newBuilder().setBoolValue(false))
                            .addValues(Value.newBuilder().setNullValue(NullValue.NULL_VALUE)))
                    .build())
            .build();
    Any anyJsonValue = Any.pack(jsonValue);
    mp.put("list_value", ImmutableList.of(false, NullValue.NULL_VALUE));
    assertThat(RuntimeHelpers.adaptProtoToValue(DYNAMIC_PROTO, anyJsonValue, CelOptions.LEGACY))
        .isEqualTo(mp);
  }

  @Test
  public void adaptProtoToValue_builderValue() throws Exception {
    CelOptions celOptions = CelOptions.LEGACY;
    assertThat(
            RuntimeHelpers.adaptProtoToValue(
                DYNAMIC_PROTO, BoolValue.newBuilder().setValue(true), celOptions))
        .isEqualTo(true);
  }

  @Test
  public void indexList() throws Exception {
    ImmutableList<String> list = ImmutableList.of("value", "value2");
    assertThat(RuntimeHelpers.indexList(list, 0.0)).isEqualTo("value");
    assertThat(RuntimeHelpers.indexList(list, UnsignedLong.valueOf(1L))).isEqualTo("value2");
    Assert.assertThrows(CelRuntimeException.class, () -> RuntimeHelpers.indexList(list, 1.1));
  }
}
