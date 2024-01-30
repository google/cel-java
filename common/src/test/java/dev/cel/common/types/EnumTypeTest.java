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

package dev.cel.common.types;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EnumTypeTest {

  private EnumType enumType;

  @Before
  public void setUp() {
    ImmutableMap<String, Integer> enumItems =
        ImmutableMap.of("NOT_SPECIFIED", 0, "INT_TYPE", 1, "STRING_TYPE", 2);
    enumType = EnumType.create("MyCustomEnum", enumItems);
  }

  @Test
  public void findNumberByName() {
    assertThat(enumType.findNameByNumber(1)).hasValue("INT_TYPE");
    assertThat(enumType.findNameByNumber(42)).isEmpty();
  }

  @Test
  public void findNameByNumber() {
    assertThat(enumType.findNumberByName("STRING_TYPE")).hasValue(2);
    assertThat(enumType.findNumberByName("DOUBLE_TYPE")).isEmpty();
  }
}
