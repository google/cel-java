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
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class OptionalValueTest {

  @Test
  public void emptyOptional() {
    OptionalValue<Object, Object> optionalValue = OptionalValue.EMPTY;

    assertThat(optionalValue.isZeroValue()).isTrue();
    NoSuchElementException exception =
        assertThrows(NoSuchElementException.class, optionalValue::value);
    assertThat(exception).hasMessageThat().isEqualTo("No value present");
  }

  @Test
  public void optionalValue_selectEmpty() {
    OptionalValue<?, ?> optionalValue = OptionalValue.EMPTY.select("bogus");

    assertThat(optionalValue).isEqualTo(OptionalValue.EMPTY);
    assertThat(optionalValue.isZeroValue()).isTrue();
  }

  @Test
  public void optionalValue_construct() {
    OptionalValue<Long, Object> optionalValue = OptionalValue.create(1L);

    assertThat(optionalValue.value()).isEqualTo(1L);
    assertThat(optionalValue.isZeroValue()).isFalse();
  }

  @Test
  public void optSelectField_map_success() {
    Long one = 1L;
    String hello = "hello";
    ImmutableMap<Long, String> mapValue = ImmutableMap.of(one, hello);
    OptionalValue<ImmutableMap<Long, String>, Long> optionalValueContainingMap =
        OptionalValue.create(mapValue);

    assertThat(optionalValueContainingMap.select(one)).isEqualTo(OptionalValue.create(hello));
  }

  @Test
  public void optSelectField_map_returnsEmpty() {
    Long one = 1L;
    String hello = "hello";
    ImmutableMap<Long, String> mapValue = ImmutableMap.of(one, hello);
    OptionalValue<ImmutableMap<Long, String>, Object> optionalValueContainingMap =
        OptionalValue.create(mapValue);

    assertThat(optionalValueContainingMap.select(NullValue.NULL_VALUE))
        .isEqualTo(OptionalValue.EMPTY);
  }

  @Test
  public void optSelectField_struct_success() {
    CelCustomStruct celCustomStruct = new CelCustomStruct(5L);
    OptionalValue<CelCustomStruct, String> optionalValueContainingStruct =
        OptionalValue.create(celCustomStruct);

    assertThat(optionalValueContainingStruct.select("data")).isEqualTo(OptionalValue.create(5L));
  }

  @Test
  public void optSelectField_struct_returnsEmpty() {
    CelCustomStruct celCustomStruct = new CelCustomStruct(5L);
    OptionalValue<CelCustomStruct, String> optionalValueContainingStruct =
        OptionalValue.create(celCustomStruct);

    assertThat(optionalValueContainingStruct.select("bogus")).isEqualTo(OptionalValue.EMPTY);
  }

  @Test
  @TestParameters("{key: 1, expectedResult: true}")
  @TestParameters("{key: 100, expectedResult: false}")
  public void findField_map_success(long key, boolean expectedResult) {
    Long one = 1L;
    String hello = "hello";
    ImmutableMap<Long, String> mapValue = ImmutableMap.of(one, hello);
    OptionalValue<ImmutableMap<Long, String>, Long> optionalValueContainingMap =
        OptionalValue.create(mapValue);

    assertThat(optionalValueContainingMap.find(key).isPresent()).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{field: 'data', expectedResult: true}")
  @TestParameters("{field: 'bogus', expectedResult: false}")
  public void findField_struct_success(String field, boolean expectedResult) {
    CelCustomStruct celCustomStruct = new CelCustomStruct(5L);
    OptionalValue<CelCustomStruct, String> optionalValueContainingStruct =
        OptionalValue.create(celCustomStruct);

    assertThat(optionalValueContainingStruct.find(field).isPresent()).isEqualTo(expectedResult);
  }

  @Test
  public void findField_onEmptyOptional() {
    assertThat(OptionalValue.EMPTY.find("bogus")).isEmpty();
  }

  @Test
  public void create_nullValue_throws() {
    assertThrows(NullPointerException.class, () -> OptionalValue.create(null));
  }

  @Test
  public void celTypeTest() {
    OptionalValue<Object, Object> value = OptionalValue.EMPTY;

    assertThat(value.celType()).isEqualTo(OptionalType.create(SimpleType.DYN));
  }

  @SuppressWarnings("Immutable") // Test only
  private static class CelCustomStruct extends StructValue<String> {
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
    public Long select(String field) {
      return find(field).get();
    }

    @Override
    public Optional<Long> find(String field) {
      if (field.equals("data")) {
        return Optional.of(value());
      }

      return Optional.empty();
    }

    private CelCustomStruct(long data) {
      this.data = data;
    }
  }
}
