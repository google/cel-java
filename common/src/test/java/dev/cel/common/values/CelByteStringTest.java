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

import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelByteStringTest {

  @Test
  public void emptyBytes() {
    CelByteString byteString = CelByteString.of(new byte[0]);

    assertThat(byteString).isEqualTo(CelByteString.EMPTY);
    assertThat(byteString.isEmpty()).isTrue();
    assertThat(byteString.toByteArray()).isEqualTo(new byte[0]);
  }

  @Test
  public void equalityTest() {
    new EqualsTester()
        .addEqualityGroup(CelByteString.of(new byte[0]), CelByteString.EMPTY)
        .addEqualityGroup(CelByteString.of(new byte[] {0xa}), CelByteString.of(new byte[] {0xa}))
        .addEqualityGroup(
            CelByteString.of(new byte[] {0x1, 0x5, 0xc}),
            CelByteString.of(new byte[] {0x1, 0x5, 0xc}))
        .addEqualityGroup(CelByteString.of(new byte[] {0xf}))
        .testEquals();
  }

  @Test
  public void sanityTest() throws Exception {
    new ClassSanityTester()
        .setDefault(CelByteString.class, CelByteString.of(new byte[] {0x1, 0x5, 0xc}))
        .setDistinctValues(
            CelByteString.class,
            CelByteString.of(new byte[] {0x1, 0x5, 0xc}),
            CelByteString.of(new byte[] {0x2, 0x6, 0xc}))
        .forAllPublicStaticMethods(CelByteString.class)
        .thatReturn(CelByteString.class)
        .testEquals()
        .testNulls();
  }

  @Test
  public void hashCode_smokeTest() {
    assertThat(CelByteString.of(new byte[0]).hashCode()).isEqualTo(1000002);
    assertThat(CelByteString.of(new byte[] {0x1}).hashCode()).isEqualTo(1000035);
    assertThat(CelByteString.of(new byte[] {0x1, 0x5}).hashCode()).isEqualTo(999846);
    assertThat(CelByteString.of(new byte[] {0x1, 0x5, 0xc}).hashCode()).isEqualTo(998020);
  }

  @Test
  public void byteString_isImmutable() {
    byte[] bytes = {0x1, 0xa, 0x3};
    CelByteString byteString = CelByteString.of(bytes);

    bytes[0] = 0x2;
    byte[] newByteArray = byteString.toByteArray();
    assertThat(newByteArray).isNotEqualTo(bytes);
    bytes[0] = 0x1;
    assertThat(newByteArray).isEqualTo(bytes);
  }

  @Test
  public void nullBytes_throws() {
    assertThrows(NullPointerException.class, () -> CelByteString.of(null));
  }
}
