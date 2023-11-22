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

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.types.CelType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import java.util.NoSuchElementException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class OptionalValueTest {

  @Test
  public void emptyOptional() {
    OptionalValue<CelValue> optionalValue = OptionalValue.EMPTY;

    assertThat(optionalValue.isZeroValue()).isTrue();
    NoSuchElementException exception =
        assertThrows(NoSuchElementException.class, optionalValue::value);
    assertThat(exception).hasMessageThat().isEqualTo("No value present");
  }

  @Test
  public void optionalValue_construct() {
    OptionalValue<IntValue> optionalValue = OptionalValue.create(IntValue.create(1L));

    assertThat(optionalValue.value()).isEqualTo(IntValue.create(1L));
    assertThat(optionalValue.isZeroValue()).isFalse();
  }

  @Test
  public void optSelectField_map_success() {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");
    ImmutableMapValue<IntValue, StringValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello));
    OptionalValue<ImmutableMapValue<IntValue, StringValue>> optionalValueContainingMap =
        OptionalValue.create(mapValue);

    assertThat(optionalValueContainingMap.select(one)).isEqualTo(OptionalValue.create(hello));
  }

  @Test
  public void optSelectField_map_returnsEmpty() {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");
    ImmutableMapValue<IntValue, StringValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello));
    OptionalValue<ImmutableMapValue<IntValue, StringValue>> optionalValueContainingMap =
        OptionalValue.create(mapValue);

    assertThat(optionalValueContainingMap.select(NullValue.NULL_VALUE))
        .isEqualTo(OptionalValue.EMPTY);
  }

  @Test
  public void optSelectField_struct_success() {
    CelCustomStruct celCustomStruct = new CelCustomStruct(5L);
    OptionalValue<CelCustomStruct> optionalValueContainingStruct =
        OptionalValue.create(celCustomStruct);

    assertThat(optionalValueContainingStruct.select(StringValue.create("data")))
        .isEqualTo(OptionalValue.create(IntValue.create(5L)));
  }

  @Test
  public void optSelectField_struct_returnsEmpty() {
    CelCustomStruct celCustomStruct = new CelCustomStruct(5L);
    OptionalValue<CelCustomStruct> optionalValueContainingStruct =
        OptionalValue.create(celCustomStruct);

    assertThat(optionalValueContainingStruct.select(StringValue.create("bogus")))
        .isEqualTo(OptionalValue.EMPTY);
  }

  @Test
  public void hasField_onEmptyOptional() {
    assertThat(OptionalValue.EMPTY.hasField(StringValue.create("bogus"))).isFalse();
  }

  @Test
  @TestParameters("{key: 1, expectedResult: true}")
  @TestParameters("{key: 100, expectedResult: false}")
  public void hasField_map_success(long key, boolean expectedResult) {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");
    ImmutableMapValue<IntValue, StringValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello));
    OptionalValue<ImmutableMapValue<IntValue, StringValue>> optionalValueContainingMap =
        OptionalValue.create(mapValue);

    assertThat(optionalValueContainingMap.hasField(IntValue.create(key))).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{field: 'data', expectedResult: true}")
  @TestParameters("{field: 'bogus', expectedResult: false}")
  public void hasField_struct_success(String field, boolean expectedResult) {
    CelCustomStruct celCustomStruct = new CelCustomStruct(5L);
    OptionalValue<CelCustomStruct> optionalValueContainingStruct =
        OptionalValue.create(celCustomStruct);

    assertThat(optionalValueContainingStruct.hasField(StringValue.create(field)))
        .isEqualTo(expectedResult);
  }

  @Test
  public void create_nullValue_throws() {
    assertThrows(NullPointerException.class, () -> OptionalValue.create(null));
  }

  @Test
  public void celTypeTest() {
    OptionalValue<CelValue> value = OptionalValue.EMPTY;

    assertThat(value.celType()).isEqualTo(OptionalType.create(SimpleType.DYN));
  }

  @SuppressWarnings("Immutable") // Test only
  private static class CelCustomStruct extends StructValue {
    private final long data;

    @Override
    public Long value() {
      return data;
    }

    @Override
    public boolean isZeroValue() {
      return data == 0;
    }

    @Override
    public CelType celType() {
      return StructTypeReference.create("customStruct");
    }

    @Override
    public CelValue select(String fieldName) {
      if (fieldName.equals("data")) {
        return IntValue.create(value());
      }

      throw new IllegalArgumentException("Invalid field name: " + fieldName);
    }

    @Override
    public boolean hasField(String fieldName) {
      return fieldName.equals("data");
    }

    private CelCustomStruct(long data) {
      this.data = data;
    }
  }
}
