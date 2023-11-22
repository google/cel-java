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
public class DoubleValueTest {

  @Test
  public void emptyDouble() {
    DoubleValue doubleValue = DoubleValue.create(0.0d);

    assertThat(doubleValue.value()).isEqualTo(0.0d);
    assertThat(doubleValue.isZeroValue()).isTrue();
  }

  @Test
  public void constructDouble() {
    DoubleValue doubleValue = DoubleValue.create(5.0d);

    assertThat(doubleValue.value()).isEqualTo(5.0d);
    assertThat(doubleValue.isZeroValue()).isFalse();
  }

  @Test
  public void celTypeTest() {
    DoubleValue value = DoubleValue.create(0.0d);

    assertThat(value.celType()).isEqualTo(SimpleType.DOUBLE);
  }

  @Test
  public void equalityTest() {
    new EqualsTester()
        .addEqualityGroup(DoubleValue.create(10.5))
        .addEqualityGroup(DoubleValue.create(0.0d), DoubleValue.create(0))
        .addEqualityGroup(DoubleValue.create(15.3), DoubleValue.create(15.3))
        .addEqualityGroup(
            DoubleValue.create(Double.MAX_VALUE), DoubleValue.create(Double.MAX_VALUE))
        .addEqualityGroup(
            DoubleValue.create(Double.MIN_VALUE), DoubleValue.create(Double.MIN_VALUE))
        .testEquals();
  }

  @Test
  public void sanityTest() throws Exception {
    new ClassSanityTester()
        .setDefault(DoubleValue.class, DoubleValue.create(100.94d))
        .setDistinctValues(DoubleValue.class, DoubleValue.create(0.0d), DoubleValue.create(100.0d))
        .forAllPublicStaticMethods(DoubleValue.class)
        .thatReturn(DoubleValue.class)
        .testEquals()
        .testNulls();
  }

  @Test
  public void hashCode_smokeTest() {
    assertThat(DoubleValue.create(0).hashCode()).isEqualTo(1000003);
    assertThat(DoubleValue.create(0.0d).hashCode()).isEqualTo(1000003);
    assertThat(DoubleValue.create(100.5d).hashCode()).isEqualTo(1079403075);
    assertThat(DoubleValue.create(Double.MAX_VALUE).hashCode()).isEqualTo(-2145435069);
    assertThat(DoubleValue.create(Double.MIN_VALUE).hashCode()).isEqualTo(1000002);
  }
}
