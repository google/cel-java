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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelValueConverterTest {
  private static final CelValueConverter CEL_VALUE_CONVERTER =
      new CelValueConverter() {};

  @Test
  public void fromJavaPrimitiveToCelValue_returnsOpaqueValue() {
    OpaqueValue opaqueValue =
        (OpaqueValue) CEL_VALUE_CONVERTER.fromJavaPrimitiveToCelValue(new UserDefinedClass());

    assertThat(opaqueValue.celType().name()).contains("UserDefinedClass");
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void fromJavaObjectToCelValue_optionalValue() {
    OptionalValue<StringValue> optionalValue =
        (OptionalValue<StringValue>)
            CEL_VALUE_CONVERTER.fromJavaObjectToCelValue(Optional.of("test"));

    assertThat(optionalValue).isEqualTo(OptionalValue.create(StringValue.create("test")));
  }

  @Test
  public void fromJavaObjectToCelValue_errorValue() {
    IllegalArgumentException e = new IllegalArgumentException("error");

    ErrorValue errorValue = (ErrorValue) CEL_VALUE_CONVERTER.fromJavaObjectToCelValue(e);

    assertThat(errorValue.value()).isEqualTo(e);
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void fromCelValueToJavaObject_mapValue() {
    ImmutableMap<String, Long> result =
        (ImmutableMap<String, Long>)
            CEL_VALUE_CONVERTER.fromCelValueToJavaObject(
                ImmutableMapValue.create(
                    ImmutableMap.of(StringValue.create("test"), IntValue.create(1))));

    assertThat(result).containsExactly("test", 1L);
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void fromCelValueToJavaObject_listValue() {
    ImmutableList<Boolean> result =
        (ImmutableList<Boolean>)
            CEL_VALUE_CONVERTER.fromCelValueToJavaObject(
                ImmutableListValue.create(ImmutableList.of(BoolValue.create(true))));

    assertThat(result).containsExactly(true);
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void fromCelValueToJavaObject_optionalValue() {
    Optional<Long> result =
        (Optional<Long>)
            CEL_VALUE_CONVERTER.fromCelValueToJavaObject(OptionalValue.create(IntValue.create(2)));

    assertThat(result).isEqualTo(Optional.of(2L));
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void fromCelValueToJavaObject_emptyOptionalValue() {
    Optional<Long> result =
        (Optional<Long>) CEL_VALUE_CONVERTER.fromCelValueToJavaObject(OptionalValue.EMPTY);

    assertThat(result).isEqualTo(Optional.empty());
  }

  private static class UserDefinedClass {}
}
