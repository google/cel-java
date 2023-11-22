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

import dev.cel.common.types.SimpleType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BytesValueTest {

  @Test
  public void emptyBytes() {
    BytesValue bytesValue = BytesValue.create(CelByteString.EMPTY);

    assertThat(bytesValue.value()).isEqualTo(CelByteString.of(new byte[0]));
    assertThat(bytesValue.isZeroValue()).isTrue();
  }

  @Test
  public void constructBytes() {
    BytesValue bytesValue = BytesValue.create(CelByteString.of(new byte[] {0x1, 0x5, 0xc}));

    assertThat(bytesValue.value()).isEqualTo(CelByteString.of(new byte[] {0x1, 0x5, 0xc}));
    assertThat(bytesValue.isZeroValue()).isFalse();
  }

  @Test
  public void celTypeTest() {
    BytesValue value = BytesValue.create(CelByteString.EMPTY);

    assertThat(value.celType()).isEqualTo(SimpleType.BYTES);
  }

  @Test
  public void create_nullValue_throws() {
    assertThrows(NullPointerException.class, () -> BytesValue.create(null));
  }
}
