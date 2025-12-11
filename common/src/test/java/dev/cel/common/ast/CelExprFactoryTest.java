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
  public void maybeDeleteId_deletesLastId() {
    CelExprFactory exprFactory = CelExprFactory.newInstance();
    long id1 = exprFactory.nextExprId(); // 1
    assertThat(id1).isEqualTo(1L);

    exprFactory.maybeDeleteId(id1);

    // Should be reused
    assertThat(exprFactory.nextExprId()).isEqualTo(1L);
  }

  @Test
  public void maybeDeleteId_doesNotDeletePreviouslyAllocatedId() {
    CelExprFactory exprFactory = CelExprFactory.newInstance();
    long id1 = exprFactory.nextExprId(); // 1
    long id2 = exprFactory.nextExprId(); // 2

    // Try to delete id1. Since id2 was allocated after, it should NOT delete id1
    // because that would rewind the counter and cause a collision with id2.
    exprFactory.maybeDeleteId(id1);

    // Should NOT be reused. Next should be 3.
    assertThat(exprFactory.nextExprId()).isEqualTo(3L);
  }
}
