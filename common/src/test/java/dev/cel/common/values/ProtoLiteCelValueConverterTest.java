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

package dev.cel.common.values;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.DefaultLiteDescriptorPool;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypesProto3CelDescriptor;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoLiteCelValueConverterTest {
  private static final CelLiteDescriptorPool DESCRIPTOR_POOL =
      DefaultLiteDescriptorPool.newInstance(
          ImmutableSet.of(TestAllTypesProto3CelDescriptor.getDescriptor()));

  private static final ProtoLiteCelValueConverter PROTO_LITE_CEL_VALUE_CONVERTER =
      ProtoLiteCelValueConverter.newInstance(DESCRIPTOR_POOL);

  @Test
  public void fromProtoMessageToCelValue_withTestMessage_convertsToProtoMessageLiteValue() {
    ProtoMessageLiteValue protoMessageLiteValue =
        (ProtoMessageLiteValue)
            PROTO_LITE_CEL_VALUE_CONVERTER.fromProtoMessageToCelValue(
                "cel.expr.conformance.proto3.TestAllTypes", TestAllTypes.getDefaultInstance());

    assertThat(protoMessageLiteValue.value()).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  private enum WellKnownProtoTestCase {
    BOOL(WellKnownProto.BOOL_VALUE, com.google.protobuf.BoolValue.of(true), BoolValue.create(true)),
    BYTES(
        WellKnownProto.BYTES_VALUE,
        com.google.protobuf.BytesValue.of(ByteString.copyFromUtf8("test")),
        BytesValue.create(CelByteString.of("test".getBytes(UTF_8)))),
    FLOAT(WellKnownProto.FLOAT_VALUE, FloatValue.of(1.0f), DoubleValue.create(1.0f)),
    DOUBLE(
        WellKnownProto.DOUBLE_VALUE,
        com.google.protobuf.DoubleValue.of(1.0),
        DoubleValue.create(1.0)),
    INT32(WellKnownProto.INT32_VALUE, Int32Value.of(1), IntValue.create(1)),
    INT64(WellKnownProto.INT64_VALUE, Int64Value.of(1L), IntValue.create(1L)),
    STRING(
        WellKnownProto.STRING_VALUE,
        com.google.protobuf.StringValue.of("test"),
        StringValue.create("test")),

    DURATION(
        WellKnownProto.DURATION,
        Duration.newBuilder().setSeconds(10).setNanos(50).build(),
        DurationValue.create(java.time.Duration.ofSeconds(10, 50))),
    TIMESTAMP(
        WellKnownProto.TIMESTAMP,
        Timestamp.newBuilder().setSeconds(1678886400L).setNanos(123000000).build(),
        TimestampValue.create(Instant.ofEpochSecond(1678886400L, 123000000))),
    UINT32(WellKnownProto.UINT32_VALUE, UInt32Value.of(1), UintValue.create(1)),
    UINT64(WellKnownProto.UINT64_VALUE, UInt64Value.of(1L), UintValue.create(1L)),
    ;

    private final WellKnownProto wellKnownProto;
    private final MessageLite msg;
    private final CelValue celValue;

    WellKnownProtoTestCase(WellKnownProto wellKnownProto, MessageLite msg, CelValue celValue) {
      this.wellKnownProto = wellKnownProto;
      this.msg = msg;
      this.celValue = celValue;
    }
  }

  @Test
  public void fromProtoMessageToCelValue_withWellKnownProto_convertsToEquivalentCelValue(
      @TestParameter WellKnownProtoTestCase testCase) {
    CelValue convertedCelValue =
        PROTO_LITE_CEL_VALUE_CONVERTER.fromProtoMessageToCelValue(
            testCase.wellKnownProto.typeName(), testCase.msg);

    assertThat(convertedCelValue).isEqualTo(testCase.celValue);
  }
}
