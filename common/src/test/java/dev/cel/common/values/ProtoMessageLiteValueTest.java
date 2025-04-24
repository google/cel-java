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

import com.google.common.collect.ImmutableSet;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.internal.DefaultLiteDescriptorPool;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypesCelDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoMessageLiteValueTest {
  private static final CelLiteDescriptorPool DESCRIPTOR_POOL =
      DefaultLiteDescriptorPool.newInstance(
          ImmutableSet.of(TestAllTypesCelDescriptor.getDescriptor()));

  private static final ProtoLiteCelValueConverter PROTO_LITE_CEL_VALUE_CONVERTER =
      ProtoLiteCelValueConverter.newInstance(DESCRIPTOR_POOL);

  @Test
  public void create_withEmptyMessage() {
    ProtoMessageLiteValue messageLiteValue =
        ProtoMessageLiteValue.create(
            TestAllTypes.getDefaultInstance(),
            "cel.expr.conformance.proto3.TestAllTypes",
            PROTO_LITE_CEL_VALUE_CONVERTER);

    assertThat(messageLiteValue.value()).isEqualTo(TestAllTypes.getDefaultInstance());
    assertThat(messageLiteValue.isZeroValue()).isTrue();
  }

  @Test
  public void create_withPopulatedMessage() {
    ProtoMessageLiteValue messageLiteValue =
        ProtoMessageLiteValue.create(
            TestAllTypes.newBuilder().setSingleInt64(1L).build(),
            "cel.expr.conformance.proto3.TestAllTypes",
            PROTO_LITE_CEL_VALUE_CONVERTER);

    assertThat(messageLiteValue.value())
        .isEqualTo(TestAllTypes.newBuilder().setSingleInt64(1L).build());
    assertThat(messageLiteValue.isZeroValue()).isFalse();
  }
}
