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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.common.ast.CelExprIdGeneratorFactory.StableIdGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelExprIdGeneratorTest {
  @Test
  public void newMonotonicIdGenerator_throwsIfIdIsNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CelExprIdGeneratorFactory.newMonotonicIdGenerator(-1));
  }

  @Test
  public void newStableIdGenerator_throwsIfIdIsNegative() {
    assertThrows(
        IllegalArgumentException.class, () -> CelExprIdGeneratorFactory.newStableIdGenerator(-1));
  }

  @Test
  public void stableIdGenerator_renumberId() {
    StableIdGenerator idGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(0);

    assertThat(idGenerator.renumberId(0)).isEqualTo(0);
    assertThat(idGenerator.renumberId(2)).isEqualTo(1);
    assertThat(idGenerator.renumberId(2)).isEqualTo(1);
    assertThat(idGenerator.renumberId(1)).isEqualTo(2);
    assertThat(idGenerator.renumberId(3)).isEqualTo(3);
  }
}
