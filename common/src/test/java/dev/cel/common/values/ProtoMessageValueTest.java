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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.types.StructTypeReference;
import dev.cel.testing.testdata.proto2.MessagesProto2Extensions;
import dev.cel.testing.testdata.proto2.Proto2Message;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes.NestedEnum;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes.NestedMessage;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProtoMessageValueTest {

  private static final ProtoCelValueConverter PROTO_CEL_VALUE_CONVERTER =
      ProtoCelValueConverter.newInstance(
          CelOptions.current().enableUnsignedLongs(true).build(),
          DefaultDescriptorPool.INSTANCE,
          DynamicProto.create(DefaultMessageFactory.INSTANCE));

  @Test
  public void emptyProtoMessage() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            TestAllTypes.getDefaultInstance(),
            DefaultDescriptorPool.INSTANCE,
            PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.value()).isEqualTo(TestAllTypes.getDefaultInstance());
    assertThat(protoMessageValue.isZeroValue()).isTrue();
  }

  @Test
  public void constructProtoMessage() {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder().setSingleBool(true).setSingleInt64(5L).build();
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.value()).isEqualTo(testAllTypes);
    assertThat(protoMessageValue.isZeroValue()).isFalse();
  }

  @Test
  public void hasField_fieldIsSet_success() {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder()
            .setSingleBool(true)
            .setSingleInt64(5L)
            .addRepeatedInt64(5L)
            .build();
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.hasField("single_bool")).isTrue();
    assertThat(protoMessageValue.hasField("single_int64")).isTrue();
    assertThat(protoMessageValue.hasField("repeated_int64")).isTrue();
  }

  @Test
  public void hasField_fieldIsUnset_success() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            TestAllTypes.getDefaultInstance(),
            DefaultDescriptorPool.INSTANCE,
            PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.hasField("single_int32")).isFalse();
    assertThat(protoMessageValue.hasField("single_uint64")).isFalse();
    assertThat(protoMessageValue.hasField("repeated_int32")).isFalse();
  }

  @Test
  public void hasField_fieldIsUndeclared_throwsException() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            TestAllTypes.getDefaultInstance(),
            DefaultDescriptorPool.INSTANCE,
            PROTO_CEL_VALUE_CONVERTER);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> protoMessageValue.hasField("bogus"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "field 'bogus' is not declared in message"
                + " 'dev.cel.testing.testdata.proto2.TestAllTypes'");
  }

  @Test
  public void hasField_extensionField_success() {
    CelDescriptorPool descriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                ImmutableList.of(MessagesProto2Extensions.getDescriptor())));
    ProtoCelValueConverter protoCelValueConverter =
        ProtoCelValueConverter.newInstance(
            CelOptions.DEFAULT,
            DefaultDescriptorPool.INSTANCE,
            DynamicProto.create(DefaultMessageFactory.create(descriptorPool)));
    Proto2Message proto2Message =
        Proto2Message.newBuilder().setExtension(MessagesProto2Extensions.int32Ext, 1).build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(proto2Message, descriptorPool, protoCelValueConverter);

    assertThat(protoMessageValue.hasField("dev.cel.testing.testdata.proto2.int32_ext")).isTrue();
  }

  @Test
  public void hasField_extensionField_throwsWhenDescriptorMissing() {
    Proto2Message proto2Message =
        Proto2Message.newBuilder().setExtension(MessagesProto2Extensions.int32Ext, 1).build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            proto2Message, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> protoMessageValue.hasField("dev.cel.testing.testdata.proto2.int32_ext"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "field 'dev.cel.testing.testdata.proto2.int32_ext' is not declared in message"
                + " 'dev.cel.testing.testdata.proto2.Proto2Message'");
  }

  private enum SelectFieldTestCase {
    // Primitives
    BOOL("single_bool", BoolValue.create(true)),
    INT32("single_int32", IntValue.create(4L)),
    INT64("single_int64", IntValue.create(5L)),
    UINT32("single_uint32", UintValue.create(UnsignedLong.valueOf(1L))),
    UINT64("single_uint64", UintValue.create(UnsignedLong.MAX_VALUE)),
    FLOAT("single_float", DoubleValue.create(1.5d)),
    DOUBLE("single_double", DoubleValue.create(2.5d)),
    STRING("single_string", StringValue.create("test")),
    BYTES("single_bytes", BytesValue.create(CelByteString.of(new byte[] {0x01}))),
    // Well known types
    ANY("single_any", BoolValue.create(true)),
    DURATION("single_duration", DurationValue.create(Duration.ofSeconds(100))),
    TIMESTAMP("single_timestamp", TimestampValue.create(Instant.ofEpochSecond(100))),
    INT32_WRAPPER("single_int32_wrapper", IntValue.create(5L)),
    INT64_WRAPPER("single_int64_wrapper", IntValue.create(10L)),
    UINT32_WRAPPER("single_uint32_wrapper", UintValue.create(UnsignedLong.valueOf(1L))),
    UINT64_WRAPPER("single_uint64_wrapper", UintValue.create(UnsignedLong.MAX_VALUE)),
    FLOAT_WRAPPER("single_float_wrapper", DoubleValue.create(7.5d)),
    DOUBLE_WRAPPER("single_double_wrapper", DoubleValue.create(8.5d)),
    STRING_WRAPPER("single_string_wrapper", StringValue.create("hello")),
    BYTES_WRAPPER("single_bytes_wrapper", BytesValue.create(CelByteString.of(new byte[] {0x02}))),
    REPEATED_INT64(
        "repeated_int64", ImmutableListValue.create(ImmutableList.of(IntValue.create(5L)))),
    MAP_STRING_STRING(
        "map_string_string",
        ImmutableMapValue.create(
            ImmutableMap.of(StringValue.create("a"), StringValue.create("b")))),
    NESTED_MESSAGE(
        "standalone_message",
        ProtoMessageValue.create(
            NestedMessage.getDefaultInstance(),
            DefaultDescriptorPool.INSTANCE,
            PROTO_CEL_VALUE_CONVERTER)),
    NESTED_ENUM("standalone_enum", IntValue.create(1L));

    private final String fieldName;
    private final CelValue celValue;

    SelectFieldTestCase(String fieldName, CelValue celValue) {
      this.fieldName = fieldName;
      this.celValue = celValue;
    }
  }

  @Test
  public void selectField_success(@TestParameter SelectFieldTestCase testCase) {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder()
            .setSingleBool(true)
            .setSingleInt32(4)
            .setSingleInt64(5L)
            .setSingleUint32(1)
            .setSingleUint64(UnsignedLong.MAX_VALUE.longValue())
            .setSingleFloat(1.5f)
            .setSingleDouble(2.5d)
            .setSingleString("test")
            .setSingleBytes(ByteString.copyFrom(new byte[] {0x01}))
            .setSingleAny(
                Any.pack(DynamicMessage.newBuilder(com.google.protobuf.BoolValue.of(true)).build()))
            .setSingleDuration(com.google.protobuf.Duration.newBuilder().setSeconds(100))
            .setSingleTimestamp(Timestamp.newBuilder().setSeconds(100))
            .setSingleInt32Wrapper(Int32Value.of(5))
            .setSingleInt64Wrapper(Int64Value.of(10L))
            .setSingleUint32Wrapper(UInt32Value.of(1))
            .setSingleUint64Wrapper(UInt64Value.of(UnsignedLong.MAX_VALUE.longValue()))
            .setSingleStringWrapper(com.google.protobuf.StringValue.of("hello"))
            .setSingleFloatWrapper(FloatValue.of(7.5f))
            .setSingleDoubleWrapper(com.google.protobuf.DoubleValue.of(8.5d))
            .setSingleBytesWrapper(
                com.google.protobuf.BytesValue.of(ByteString.copyFrom(new byte[] {0x02})))
            .addRepeatedInt64(5L)
            .putMapStringString("a", "b")
            .setStandaloneMessage(NestedMessage.getDefaultInstance())
            .setStandaloneEnum(NestedEnum.BAR)
            .build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select(testCase.fieldName)).isEqualTo(testCase.celValue);
  }

  @Test
  public void selectField_dynamicMessage_success() {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder().setSingleInt32Wrapper(Int32Value.of(5)).build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            DynamicMessage.newBuilder(testAllTypes).build(),
            DefaultDescriptorPool.INSTANCE,
            PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select("single_int32_wrapper")).isEqualTo(IntValue.create(5));
  }

  @Test
  @TestParameters("{nanos: 1000000001}")
  @TestParameters("{nanos: -1000000001}")
  public void selectField_timestampNanosOutOfRange_success(int nanos) {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder()
            .setSingleTimestamp(Timestamp.newBuilder().setNanos(nanos))
            .build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select("single_timestamp"))
        .isEqualTo(TimestampValue.create(Instant.ofEpochSecond(0, nanos)));
  }

  @Test
  @TestParameters("{seconds: 0, nanos: 1000000001}")
  @TestParameters("{seconds: 0, nanos: -1000000001}")
  @TestParameters("{seconds: -10, nanos: 1000000001}")
  @TestParameters("{seconds: 10, nanos: -1000000001}")
  public void selectField_durationOutOfRange_success(int seconds, int nanos) {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder()
            .setSingleDuration(
                com.google.protobuf.Duration.newBuilder().setSeconds(seconds).setNanos(nanos))
            .build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select("single_duration"))
        .isEqualTo(DurationValue.create(Duration.ofSeconds(seconds, nanos)));
  }

  private enum SelectFieldJsonValueTestCase {
    NULL(Value.newBuilder().build(), NullValue.NULL_VALUE),
    BOOL(Value.newBuilder().setBoolValue(true).build(), BoolValue.create(true)),
    DOUBLE(Value.newBuilder().setNumberValue(4.5d).build(), DoubleValue.create(4.5d)),
    STRING(Value.newBuilder().setStringValue("test").build(), StringValue.create("test")),
    STRUCT(
        Value.newBuilder()
            .setStructValue(
                Struct.newBuilder()
                    .putFields("a", Value.newBuilder().setBoolValue(false).build())
                    .build())
            .build(),
        ImmutableMapValue.create(
            ImmutableMap.of(StringValue.create("a"), BoolValue.create(false)))),
    LIST(
        Value.newBuilder()
            .setListValue(
                com.google.protobuf.ListValue.newBuilder()
                    .addValues(Value.newBuilder().setStringValue("test").build())
                    .build())
            .build(),
        ImmutableListValue.create(ImmutableList.of(StringValue.create("test"))));

    private final Value jsonValue;
    private final CelValue celValue;

    SelectFieldJsonValueTestCase(Value jsonValue, CelValue celValue) {
      this.jsonValue = jsonValue;
      this.celValue = celValue;
    }
  }

  @Test
  public void selectField_jsonValue(@TestParameter SelectFieldJsonValueTestCase testCase) {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder().setSingleValue(testCase.jsonValue).build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select("single_value")).isEqualTo(testCase.celValue);
  }

  @Test
  public void selectField_jsonStruct() {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder()
            .setSingleStruct(
                Struct.newBuilder()
                    .putFields("a", Value.newBuilder().setBoolValue(false).build())
                    .build())
            .build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select("single_struct"))
        .isEqualTo(
            ImmutableMapValue.create(
                ImmutableMap.of(StringValue.create("a"), BoolValue.create(false))));
  }

  @Test
  public void selectField_jsonList() {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder()
            .setListValue(
                com.google.protobuf.ListValue.newBuilder()
                    .addValues(Value.newBuilder().setBoolValue(false).build())
                    .build())
            .build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            testAllTypes, DefaultDescriptorPool.INSTANCE, PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select("list_value"))
        .isEqualTo(ImmutableListValue.create(ImmutableList.of(BoolValue.create(false))));
  }

  @Test
  public void selectField_wrapperFieldUnset_returnsNull() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            TestAllTypes.getDefaultInstance(),
            DefaultDescriptorPool.INSTANCE,
            PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.select("single_int64_wrapper")).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  public void celTypeTest() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(
            TestAllTypes.getDefaultInstance(),
            DefaultDescriptorPool.INSTANCE,
            PROTO_CEL_VALUE_CONVERTER);

    assertThat(protoMessageValue.celType())
        .isEqualTo(StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));
  }
}
