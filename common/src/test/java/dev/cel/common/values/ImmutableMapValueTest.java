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
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ImmutableMapValueTest {

  @Test
  public void emptyMap() {
    ImmutableMapValue<CelValue, CelValue> mapValue = ImmutableMapValue.create(ImmutableMap.of());

    assertThat(mapValue.value()).isEmpty();
    assertThat(mapValue.isZeroValue()).isTrue();
  }

  @Test
  public void mapValue_construct() {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");

    ImmutableMapValue<IntValue, StringValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello));

    assertThat(mapValue.value()).containsExactly(one, hello);
    assertThat(mapValue.isZeroValue()).isFalse();
  }

  @Test
  public void get_success() {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");
    ImmutableMapValue<IntValue, StringValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello));

    assertThat(mapValue.get(one)).isEqualTo(hello);
  }

  @Test
  public void get_nonExistentKey_throws() {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");
    ImmutableMapValue<IntValue, StringValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> mapValue.get(IntValue.create(100L)));
    assertThat(exception).hasMessageThat().isEqualTo("key '100' is not present in map.");
  }

  @Test
  @TestParameters("{key: 1, expectedResult: true}")
  @TestParameters("{key: 100, expectedResult: false}")
  public void has_success(long key, boolean expectedResult) {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");
    ImmutableMapValue<IntValue, StringValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello));

    assertThat(mapValue.has(IntValue.create(key))).isEqualTo(expectedResult);
  }

  @Test
  public void mapValue_mixedTypes() {
    IntValue one = IntValue.create(1L);
    StringValue hello = StringValue.create("hello");

    ImmutableMapValue<CelValue, CelValue> mapValue =
        ImmutableMapValue.create(ImmutableMap.of(one, hello, hello, one));

    assertThat(mapValue.value()).containsExactly(one, hello, hello, one);
    assertThat(mapValue.isZeroValue()).isFalse();
  }

  @Test
  public void create_nullValue_throws() {
    assertThrows(NullPointerException.class, () -> ImmutableMapValue.create(null));
  }
}
