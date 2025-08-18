// Copyright 2024 Google LLC
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

package dev.cel.common;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.values.CelByteString;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelProtoJsonAdapterTest {

  @Test
  public void adaptValueToJsonValue_asymmetricJsonConversion() {
    assertThat(CelProtoJsonAdapter.adaptValueToJsonValue(UnsignedLong.valueOf(1L)))
        .isEqualTo(Value.newBuilder().setNumberValue(1).build());
    assertThat(CelProtoJsonAdapter.adaptValueToJsonValue(UnsignedLong.fromLongBits(-1L)))
        .isEqualTo(Value.newBuilder().setStringValue(Long.toUnsignedString(-1L)).build());
    assertThat(CelProtoJsonAdapter.adaptValueToJsonValue(1L))
        .isEqualTo(Value.newBuilder().setNumberValue(1).build());
    assertThat(CelProtoJsonAdapter.adaptValueToJsonValue(Long.MAX_VALUE))
        .isEqualTo(Value.newBuilder().setStringValue(Long.toString(Long.MAX_VALUE)).build());
    assertThat(CelProtoJsonAdapter.adaptValueToJsonValue(CelByteString.copyFromUtf8("foo")))
        .isEqualTo(Value.newBuilder().setStringValue("Zm9v").build());
  }

  @Test
  public void adaptValueToJsonList() {
    ListValue listValue = CelProtoJsonAdapter.adaptToJsonListValue(Arrays.asList("hello", "world"));

    assertThat(listValue)
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue("hello"))
                .addValues(Value.newBuilder().setStringValue("world"))
                .build());
  }

  @Test
  public void adaptToJsonStructValue() {
    Struct struct = CelProtoJsonAdapter.adaptToJsonStructValue(ImmutableMap.of("key", "value"));

    assertThat(struct)
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Value.newBuilder().setStringValue("value").build())
                .build());
  }

  @Test
  public void adaptValueToJsonValue_unsupportedJsonConversion() {
    assertThrows(
        ClassCastException.class,
        () -> CelProtoJsonAdapter.adaptValueToJsonValue(ImmutableMap.of(1, 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> CelProtoJsonAdapter.adaptValueToJsonValue(CelSource.newBuilder().build()));
  }
}
