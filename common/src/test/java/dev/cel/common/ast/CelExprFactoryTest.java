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

import dev.cel.common.ast.CelExprIdGeneratorFactory.StableIdGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelExprFactoryTest {

  @Test
  public void construct_success() {
    CelExprFactory exprFactory = CelExprFactory.newInstance();

    assertThat(exprFactory).isNotNull();
  }

  @Test
  public void nextExprId_startingDefaultIsOne() {
    CelExprFactory exprFactory = CelExprFactory.newInstance();

    assertThat(exprFactory.nextExprId()).isEqualTo(1L);
    assertThat(exprFactory.nextExprId()).isEqualTo(2L);
  }

  @Test
  public void nextExprId_usingStableIdGenerator() {
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(0);
    CelExprFactory exprFactory = CelExprFactory.newInstance(stableIdGenerator::nextExprId);

    assertThat(exprFactory.nextExprId()).isEqualTo(1L);
    assertThat(exprFactory.nextExprId()).isEqualTo(2L);
    assertThat(stableIdGenerator.hasId(-1)).isFalse();
    assertThat(stableIdGenerator.hasId(0)).isFalse();
  }
}
