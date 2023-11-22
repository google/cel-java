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
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ImmutableListValueTest {

  @Test
  public void emptyList() {
    ListValue<CelValue> listValue = ImmutableListValue.create(ImmutableList.of());

    assertThat(listValue.value()).isEmpty();
    assertThat(listValue.isZeroValue()).isTrue();
  }

  @Test
  public void listValue_construct() {
    IntValue one = IntValue.create(1L);
    IntValue two = IntValue.create(2L);
    IntValue three = IntValue.create(3L);

    ListValue<IntValue> listValue = ImmutableListValue.create(ImmutableList.of(one, two, three));

    assertThat(listValue.value()).containsExactly(one, two, three).inOrder();
    assertThat(listValue.isZeroValue()).isFalse();
  }

  @Test
  public void listValue_mixedTypes() {
    IntValue one = IntValue.create(1L);
    DoubleValue two = DoubleValue.create(2.0d);
    StringValue three = StringValue.create("test");

    ListValue<CelValue> listValue = ImmutableListValue.create(ImmutableList.of(one, two, three));

    assertThat(listValue.value()).containsExactly(one, two, three).inOrder();
    assertThat(listValue.isZeroValue()).isFalse();
  }

  @Test
  public void create_nullValue_throws() {
    assertThrows(NullPointerException.class, () -> ImmutableListValue.create(null));
  }

  @Test
  public void celTypeTest() {
    ListValue<CelValue> value = ImmutableListValue.create(ImmutableList.of());

    assertThat(value.celType()).isEqualTo(ListType.create(SimpleType.DYN));
  }
}
