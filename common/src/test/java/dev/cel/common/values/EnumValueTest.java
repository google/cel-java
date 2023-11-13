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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EnumValueTest {

  private enum TestKind {
    ONE,
    TWO
  }

  @Test
  public void enumValue_construct() {
    EnumValue<TestKind> one = EnumValue.create(TestKind.ONE);
    EnumValue<TestKind> two = EnumValue.create(TestKind.TWO);

    assertThat(one.value()).isEqualTo(TestKind.ONE);
    assertThat(two.value()).isEqualTo(TestKind.TWO);
  }

  @Test
  public void enumValue_isZeroValue_returnsFalse() {
    assertThat(EnumValue.create(TestKind.ONE).isZeroValue()).isFalse();
    assertThat(EnumValue.create(TestKind.TWO).isZeroValue()).isFalse();
  }
}
