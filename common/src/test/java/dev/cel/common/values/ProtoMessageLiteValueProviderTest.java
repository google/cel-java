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

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.types.StructTypeReference;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypesCelDescriptor;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoMessageLiteValueProviderTest {
  private static final ProtoMessageLiteValueProvider VALUE_PROVIDER =
      ProtoMessageLiteValueProvider.newInstance(TestAllTypesCelDescriptor.getDescriptor());

  @Test
  public void newValue_unknownType_returnsEmpty() {
    assertThat(VALUE_PROVIDER.newValue("unknownType", ImmutableMap.of())).isEmpty();
  }

  @Test
  public void newValue_emptyFields_success() {
    Optional<CelValue> value =
        VALUE_PROVIDER.newValue("cel.expr.conformance.proto3.TestAllTypes", ImmutableMap.of());
    ProtoMessageLiteValue protoMessageLiteValue = (ProtoMessageLiteValue) value.get();

    assertThat(protoMessageLiteValue.value()).isEqualTo(TestAllTypes.getDefaultInstance());
    assertThat(protoMessageLiteValue.isZeroValue()).isTrue();
    assertThat(protoMessageLiteValue.celType())
        .isEqualTo(StructTypeReference.create("cel.expr.conformance.proto3.TestAllTypes"));
  }

  @Test
  public void getProtoLiteCelValueConverter() {
    assertThat(VALUE_PROVIDER.protoCelValueConverter()).isNotNull();
  }
}
