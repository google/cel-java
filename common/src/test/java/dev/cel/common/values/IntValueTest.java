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

import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.EqualsTester;
import dev.cel.common.types.SimpleType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IntValueTest {

  @Test
  public void emptyInt() {
    IntValue intValue = IntValue.create(0L);

    assertThat(intValue.value()).isEqualTo(0L);
    assertThat(intValue.isZeroValue()).isTrue();
  }

  @Test
  public void constructInt() {
    IntValue uintValue = IntValue.create(5L);

    assertThat(uintValue.value()).isEqualTo(5L);
    assertThat(uintValue.isZeroValue()).isFalse();
  }

  @Test
  public void equalityTest() {
    new EqualsTester()
        .addEqualityGroup(IntValue.create(10))
        .addEqualityGroup(IntValue.create(0), IntValue.create(0))
        .addEqualityGroup(IntValue.create(15), IntValue.create(15))
        .addEqualityGroup(IntValue.create(Long.MAX_VALUE), IntValue.create(Long.MAX_VALUE))
        .addEqualityGroup(IntValue.create(Long.MIN_VALUE), IntValue.create(Long.MIN_VALUE))
        .testEquals();
  }

  @Test
  public void sanityTest() throws Exception {
    new ClassSanityTester()
        .setDefault(IntValue.class, IntValue.create(100))
        .setDistinctValues(IntValue.class, IntValue.create(0), IntValue.create(100))
        .forAllPublicStaticMethods(IntValue.class)
        .thatReturn(IntValue.class)
        .testEquals()
        .testNulls();
  }

  @Test
  public void hashCode_smokeTest() {
    assertThat(IntValue.create(0).hashCode()).isEqualTo(1000003);
    assertThat(IntValue.create(100).hashCode()).isEqualTo(999975);
    assertThat(IntValue.create(Long.MAX_VALUE).hashCode()).isEqualTo(-2146483645);
    assertThat(IntValue.create(Long.MIN_VALUE).hashCode()).isEqualTo(-2146483645);
  }

  @Test
  public void celTypeTest() {
    IntValue value = IntValue.create(0);

    assertThat(value.celType()).isEqualTo(SimpleType.INT);
  }
}
