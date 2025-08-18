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
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoMessageFactory;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import dev.cel.expr.conformance.proto2.TestAllTypesExtensions;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoMessageValueProviderTest {

  private static final CelDescriptorPool DESCRIPTOR_POOL =
      DefaultDescriptorPool.create(
          CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
              ImmutableList.of(
                  TestAllTypes.getDescriptor().getFile(), TestAllTypesExtensions.getDescriptor())));
  private static final ProtoMessageFactory MESSAGE_FACTORY =
      DefaultMessageFactory.create(DESCRIPTOR_POOL);
  private static final DynamicProto DYNAMIC_PROTO = DynamicProto.create(MESSAGE_FACTORY);

  @Test
  public void newValue_createEmptyProtoMessage() {
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);

    ProtoMessageValue protoMessageValue =
        (ProtoMessageValue)
            protoMessageValueProvider
                .newValue(TestAllTypes.getDescriptor().getFullName(), ImmutableMap.of())
                .get();

    assertThat(protoMessageValue.isZeroValue()).isTrue();
  }

  @Test
  public void newValue_createProtoMessage_fieldsPopulated() {
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);

    ProtoMessageValue protoMessageValue =
        (ProtoMessageValue)
            protoMessageValueProvider
                .newValue(
                    TestAllTypes.getDescriptor().getFullName(),
                    ImmutableMap.of(
                        "single_int32",
                        1,
                        "single_int64",
                        2,
                        "single_uint32",
                        3,
                        "single_uint64",
                        4,
                        "single_double",
                        5.5d,
                        "single_bool",
                        true,
                        "single_string",
                        "hello",
                        "single_timestamp",
                        ProtoTimeUtils.fromSecondsToTimestamp(50),
                        "single_duration",
                        ProtoTimeUtils.fromSecondsToDuration(100)))
                .get();

    assertThat(protoMessageValue.isZeroValue()).isFalse();
    assertThat(protoMessageValue.select(StringValue.create("single_int32")))
        .isEqualTo(IntValue.create(1L));
    assertThat(protoMessageValue.select(StringValue.create("single_int64")))
        .isEqualTo(IntValue.create(2L));
    assertThat(protoMessageValue.select(StringValue.create("single_uint32")))
        .isEqualTo(UintValue.create(3L));
    assertThat(protoMessageValue.select(StringValue.create("single_uint64")))
        .isEqualTo(UintValue.create(4L));
    assertThat(protoMessageValue.select(StringValue.create("single_double")))
        .isEqualTo(DoubleValue.create(5.5d));
    assertThat(protoMessageValue.select(StringValue.create("single_bool")))
        .isEqualTo(BoolValue.create(true));
    assertThat(protoMessageValue.select(StringValue.create("single_string")))
        .isEqualTo(StringValue.create("hello"));
    assertThat(protoMessageValue.select(StringValue.create("single_timestamp")))
        .isEqualTo(TimestampValue.create(Instant.ofEpochSecond(50)));
    assertThat(protoMessageValue.select(StringValue.create("single_duration")))
        .isEqualTo(DurationValue.create(Duration.ofSeconds(100)));
  }

  @Test
  public void newValue_createProtoMessage_unsignedLongFieldsPopulated() {
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);

    ProtoMessageValue protoMessageValue =
        (ProtoMessageValue)
            protoMessageValueProvider
                .newValue(
                    TestAllTypes.getDescriptor().getFullName(),
                    ImmutableMap.of(
                        "single_uint32", 3, "single_uint64", UnsignedLong.MAX_VALUE.longValue()))
                .get();

    assertThat(protoMessageValue.isZeroValue()).isFalse();
    assertThat(protoMessageValue.select(StringValue.create("single_uint32")).value())
        .isEqualTo(UnsignedLong.valueOf(3L));
    assertThat(protoMessageValue.select(StringValue.create("single_uint64")).value())
        .isEqualTo(UnsignedLong.MAX_VALUE);
  }

  @Test
  public void newValue_createProtoMessage_wrappersPopulated() {
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);

    ProtoMessageValue protoMessageValue =
        (ProtoMessageValue)
            protoMessageValueProvider
                .newValue(
                    TestAllTypes.getDescriptor().getFullName(),
                    ImmutableMap.of(
                        "single_int32_wrapper",
                        1,
                        "single_int64_wrapper",
                        2,
                        "single_uint32_wrapper",
                        3,
                        "single_uint64_wrapper",
                        4,
                        "single_double_wrapper",
                        5.5d,
                        "single_bool_wrapper",
                        true,
                        "single_string_wrapper",
                        "hello"))
                .get();

    assertThat(protoMessageValue.isZeroValue()).isFalse();
    assertThat(protoMessageValue.select(StringValue.create("single_int32_wrapper")).value())
        .isEqualTo(1L);
    assertThat(protoMessageValue.select(StringValue.create("single_int32_wrapper")).value())
        .isEqualTo(1L);
    assertThat(protoMessageValue.select(StringValue.create("single_int64_wrapper")).value())
        .isEqualTo(2L);
    assertThat(protoMessageValue.select(StringValue.create("single_uint32_wrapper")).value())
        .isEqualTo(UnsignedLong.valueOf(3L));
    assertThat(protoMessageValue.select(StringValue.create("single_uint64_wrapper")).value())
        .isEqualTo(UnsignedLong.valueOf(4L));
    assertThat(protoMessageValue.select(StringValue.create("single_double_wrapper")).value())
        .isEqualTo(5.5d);
    assertThat(protoMessageValue.select(StringValue.create("single_bool_wrapper")).value())
        .isEqualTo(true);
    assertThat(protoMessageValue.select(StringValue.create("single_string_wrapper")).value())
        .isEqualTo("hello");
  }

  @Test
  public void newValue_createProtoMessage_extensionFieldsPopulated() {
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);

    ProtoMessageValue protoMessageValue =
        (ProtoMessageValue)
            protoMessageValueProvider
                .newValue(
                    TestAllTypes.getDescriptor().getFullName(),
                    ImmutableMap.of("cel.expr.conformance.proto2.int32_ext", 1))
                .get();

    assertThat(protoMessageValue.isZeroValue()).isFalse();
    assertThat(
            protoMessageValue
                .select(StringValue.create("cel.expr.conformance.proto2.int32_ext"))
                .value())
        .isEqualTo(1);
  }

  @Test
  public void newValue_invalidMessageName_returnsEmpty() {
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);

    assertThat(protoMessageValueProvider.newValue("bogus", ImmutableMap.of())).isEmpty();
  }

  @Test
  public void newValue_invalidField_throws() {
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                protoMessageValueProvider.newValue(
                    TestAllTypes.getDescriptor().getFullName(), ImmutableMap.of("bogus", 1)));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "field 'bogus' is not declared in message"
                + " 'cel.expr.conformance.proto2.TestAllTypes'");
  }

  @Test
  public void newValue_onCombinedProvider() {
    CelValueProvider celValueProvider = (structType, fields) -> Optional.empty();
    ProtoMessageValueProvider protoMessageValueProvider =
        ProtoMessageValueProvider.newInstance(CelOptions.DEFAULT, DYNAMIC_PROTO);
    CelValueProvider combinedProvider =
        CombinedCelValueProvider.combine(celValueProvider, protoMessageValueProvider);

    ProtoMessageValue protoMessageValue =
        (ProtoMessageValue)
            combinedProvider
                .newValue(
                    TestAllTypes.getDescriptor().getFullName(), ImmutableMap.of("single_int32", 1))
                .get();

    assertThat(protoMessageValue.isZeroValue()).isFalse();
    assertThat(protoMessageValue.select(StringValue.create("single_int32")))
        .isEqualTo(IntValue.create(1L));
  }
}
