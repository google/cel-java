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

package dev.cel.common.values;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.types.NullableType;
import dev.cel.common.types.SimpleType;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoWrapperValueTest {

  private enum WrapperTestCase {
    BOOL_WRAPPER(com.google.protobuf.BoolValue.of(true), BoolValue.create(true)),
    INT32_WRAPPER(Int32Value.of(5), IntValue.create(5L)),
    INT64_WRAPPER(Int64Value.of(10), IntValue.create(10L)),
    UINT32_WRAPPER(UInt32Value.of(5), UintValue.create(5L, false)),
    UINT64_WRAPPER(
        UInt64Value.of(UnsignedLong.MAX_VALUE.longValue()),
        UintValue.create(UnsignedLong.MAX_VALUE.longValue(), false)),
    FLOAT_WRAPPER(FloatValue.of(5.4f), DoubleValue.create(5.4f)),
    DOUBLE_WRAPPER(com.google.protobuf.DoubleValue.of(6.5d), DoubleValue.create(6.5d)),
    STRING_WRAPPER(com.google.protobuf.StringValue.of("hello"), StringValue.create("hello"));

    private final Message protoMessage;
    private final CelValue wrappedCelValue;

    WrapperTestCase(Message wrapperMessage, CelValue wrappedCelValue) {
      this.protoMessage = wrapperMessage;
      this.wrappedCelValue = wrappedCelValue;
    }
  }

  @Test
  public void emptyValue() {
    ProtoWrapperValue wrapperValue = ProtoWrapperValue.create(Int64Value.of(0L), false);

    assertThat(wrapperValue.value()).isEqualTo(IntValue.create(0L));
    assertThat(wrapperValue.isZeroValue()).isTrue();
  }

  @Test
  public void constructWrapperValue(@TestParameter WrapperTestCase testCase) {
    ProtoWrapperValue wrapperValue = ProtoWrapperValue.create(testCase.protoMessage, false);

    assertThat(wrapperValue.value()).isEqualTo(testCase.wrappedCelValue);
    assertThat(wrapperValue.nativeValue()).isEqualTo(testCase.wrappedCelValue.value());
    assertThat(wrapperValue.isZeroValue()).isFalse();
  }

  @Test
  public void constructWrapperValue_unsignedLong() {
    ProtoWrapperValue wrapperValue =
        ProtoWrapperValue.create(UInt64Value.of(UnsignedLong.MAX_VALUE.longValue()), true);

    assertThat(wrapperValue.value())
        .isEqualTo(UintValue.create(UnsignedLong.MAX_VALUE.longValue(), true));
    assertThat(wrapperValue.nativeValue()).isEqualTo(UnsignedLong.MAX_VALUE);
  }

  @Test
  public void constructBytesWrapperValue() {
    ProtoWrapperValue wrapperValue =
        ProtoWrapperValue.create(
            com.google.protobuf.BytesValue.of(ByteString.copyFrom(new byte[] {0x02})), false);

    assertThat(wrapperValue.value())
        .isEqualTo(BytesValue.create(CelByteString.of(new byte[] {0x02})));
    assertThat(wrapperValue.nativeValue()).isEqualTo(ByteString.copyFrom(new byte[] {0x02}));
    assertThat(wrapperValue.isZeroValue()).isFalse();
  }

  @Test
  public void celTypeTest() {
    ProtoWrapperValue value =
        ProtoWrapperValue.create(com.google.protobuf.StringValue.of(""), false);

    assertThat(value.celType()).isEqualTo(NullableType.create(SimpleType.STRING));
  }

  @Test
  public void fieldSelection_throws() {
    ProtoWrapperValue value = ProtoWrapperValue.create(Int64Value.of(1), false);

    assertThrows(UnsupportedOperationException.class, () -> value.hasField("bogus"));
    assertThrows(UnsupportedOperationException.class, () -> value.select("bogus"));
  }

  @Test
  public void nonWrapperType_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ProtoWrapperValue.create(Value.getDefaultInstance(), false));
  }
}
