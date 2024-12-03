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

import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.NullValue;
import dev.cel.common.CelOptions;
import dev.cel.expr.conformance.proto3.NestedTestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ActivationTest {

  private static final CelOptions TEST_OPTIONS =
      CelOptions.current().enableTimestampEpoch(true).enableUnsignedLongs(true).build();

  private static final CelOptions TEST_OPTIONS_SKIP_UNSET_FIELDS =
      CelOptions.current()
          .enableTimestampEpoch(true)
          .enableUnsignedLongs(true)
          .fromProtoUnsetFieldOption(CelOptions.ProtoUnsetFieldOptions.SKIP)
          .build();

  @Test
  public void copyOf_success_withNullEntries() {
    Map<String, Object> map = new HashMap<>();
    map.put("key", null);
    map.put(null, "value");
    map.put("valid", "value");
    Activation activation = Activation.copyOf(map);
    assertThat(activation.resolve("key")).isNull();
    assertThat(activation.resolve("valid")).isEqualTo("value");
  }

  @Test
  public void fromProto() {
    NestedMessage nestedMessage = NestedMessage.newBuilder().setBb(1).build();
    Activation activation = Activation.fromProto(nestedMessage, TEST_OPTIONS);
    assertThat(activation.resolve("bb")).isEqualTo(1);

    TestAllTypes testMessage =
        TestAllTypes.newBuilder().setSingleNestedMessage(nestedMessage).build();
    activation = Activation.fromProto(testMessage, TEST_OPTIONS);
    assertThat(activation.resolve("single_nested_message")).isEqualTo(nestedMessage);
  }

  @Test
  public void fromProto_unsetScalarField() {
    Activation activation = Activation.fromProto(NestedMessage.getDefaultInstance(), TEST_OPTIONS);
    assertThat(activation.resolve("bb")).isEqualTo(0);
  }

  @Test
  public void fromProto_unsetScalarField_skipUnsetFields() {
    Activation activation =
        Activation.fromProto(NestedMessage.getDefaultInstance(), TEST_OPTIONS_SKIP_UNSET_FIELDS);
    assertThat(activation.resolve("bb")).isNull();
  }

  @Test
  public void fromProto_unsetAnyField() {
    // An unset Any field is the only field which cannot be accurately published into an Activation,
    // and is instead published as an error value which should fit nicely with the CEL evaluation
    // semantics.
    Activation activation = Activation.fromProto(TestAllTypes.getDefaultInstance(), TEST_OPTIONS);
    assertThat(activation.resolve("single_any")).isInstanceOf(Throwable.class);
    assertThat((Throwable) activation.resolve("single_any"))
        .hasMessageThat()
        .contains("illegal field value");
    assertThat((Throwable) activation.resolve("single_any"))
        .hasCauseThat()
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void fromProto_unsetValueField() {
    Activation activation = Activation.fromProto(TestAllTypes.getDefaultInstance(), TEST_OPTIONS);
    assertThat(activation.resolve("single_value")).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  public void fromProto_unsetMessageField() {
    Activation activation =
        Activation.fromProto(NestedTestAllTypes.getDefaultInstance(), TEST_OPTIONS);
    assertThat(activation.resolve("payload")).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @Test
  public void fromProto_unsetRepeatedField() {
    Activation activation = Activation.fromProto(TestAllTypes.getDefaultInstance(), TEST_OPTIONS);
    assertThat(activation.resolve("repeated_int64")).isInstanceOf(List.class);
    assertThat((List) activation.resolve("repeated_int64")).isEmpty();

    assertThat(activation.resolve("repeated_nested_message")).isInstanceOf(List.class);
    assertThat((List) activation.resolve("repeated_nested_message")).isEmpty();
  }

  @Test
  public void fromProto_unsetRepeatedField_skipUnsetFields() {
    Activation activation =
        Activation.fromProto(TestAllTypes.getDefaultInstance(), TEST_OPTIONS_SKIP_UNSET_FIELDS);
    assertThat(activation.resolve("repeated_int64")).isInstanceOf(List.class);
    assertThat((List) activation.resolve("repeated_int64")).isEmpty();

    assertThat(activation.resolve("repeated_nested_message")).isInstanceOf(List.class);
    assertThat((List) activation.resolve("repeated_nested_message")).isEmpty();
  }

  @Test
  public void fromProto_unsetMapField() {
    Activation activation = Activation.fromProto(TestAllTypes.getDefaultInstance(), TEST_OPTIONS);
    assertThat(activation.resolve("map_int32_int64")).isInstanceOf(Map.class);
    assertThat((Map) activation.resolve("map_int32_int64")).isEmpty();
  }

  @Test
  public void fromProto_unsetMapField_skipUnsetFields() {
    Activation activation =
        Activation.fromProto(TestAllTypes.getDefaultInstance(), TEST_OPTIONS_SKIP_UNSET_FIELDS);
    assertThat(activation.resolve("map_int32_int64")).isInstanceOf(Map.class);
    assertThat((Map) activation.resolve("map_int32_int64")).isEmpty();
  }

  @Test
  public void fromProto_unsignedLongField_unsignedResult() {
    Activation activation =
        Activation.fromProto(
            TestAllTypes.newBuilder()
                .setSingleUint32(1)
                .setSingleUint64(UnsignedLong.MAX_VALUE.longValue())
                .build(),
            TEST_OPTIONS);
    assertThat((UnsignedLong) activation.resolve("single_uint32")).isEqualTo(UnsignedLong.ONE);
    assertThat((UnsignedLong) activation.resolve("single_uint64"))
        .isEqualTo(UnsignedLong.MAX_VALUE);
  }

  @Test
  public void fromProto_unsignedLongField_signedResult() {
    // Test disables the unsigned long support.
    Activation activation =
        Activation.fromProto(
            TestAllTypes.newBuilder()
                .setSingleUint32(1)
                .setSingleUint64(UnsignedLong.MAX_VALUE.longValue())
                .build());
    assertThat((Long) activation.resolve("single_uint32")).isEqualTo(1L);
    assertThat((Long) activation.resolve("single_uint64")).isEqualTo(-1L);
  }
}
