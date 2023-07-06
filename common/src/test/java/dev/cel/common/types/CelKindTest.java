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

package dev.cel.common.types;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelKindTest {

  @Test
  public void isPrimitive_true() {
    assertThat(CelKind.INT.isPrimitive()).isTrue();
    assertThat(CelKind.BOOL.isPrimitive()).isTrue();
    assertThat(CelKind.UINT.isPrimitive()).isTrue();
    assertThat(CelKind.DOUBLE.isPrimitive()).isTrue();
    assertThat(CelKind.STRING.isPrimitive()).isTrue();
    assertThat(CelKind.BYTES.isPrimitive()).isTrue();
  }

  @Test
  public void isPrimitive_false() {
    assertThat(CelKind.UNSPECIFIED.isPrimitive()).isFalse();
    assertThat(CelKind.ERROR.isPrimitive()).isFalse();
    assertThat(CelKind.DYN.isPrimitive()).isFalse();
    assertThat(CelKind.ANY.isPrimitive()).isFalse();
    assertThat(CelKind.DURATION.isPrimitive()).isFalse();
    assertThat(CelKind.FUNCTION.isPrimitive()).isFalse();
    assertThat(CelKind.LIST.isPrimitive()).isFalse();
    assertThat(CelKind.MAP.isPrimitive()).isFalse();
    assertThat(CelKind.NULL_TYPE.isPrimitive()).isFalse();
    assertThat(CelKind.OPAQUE.isPrimitive()).isFalse();
    assertThat(CelKind.STRUCT.isPrimitive()).isFalse();
    assertThat(CelKind.TIMESTAMP.isPrimitive()).isFalse();
    assertThat(CelKind.TYPE.isPrimitive()).isFalse();
    assertThat(CelKind.TYPE_PARAM.isPrimitive()).isFalse();
  }
}
