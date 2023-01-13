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

package dev.cel.common;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelSourceLocationTest {

  @Test
  public void equals_equivalent() throws Exception {
    CelSourceLocation lhs = CelSourceLocation.of(1, 1);
    CelSourceLocation rhs = CelSourceLocation.of(1, 1);
    assertThat(lhs).isEqualTo(rhs);
    assertThat(lhs.hashCode()).isEqualTo(rhs.hashCode());
    assertThat(lhs.compareTo(rhs)).isEqualTo(0);
  }

  @Test
  public void equals_notEquivalent() throws Exception {
    CelSourceLocation lhs = CelSourceLocation.of(1, 1);
    CelSourceLocation rhs = CelSourceLocation.of(2, 2);
    assertThat(lhs).isNotEqualTo(rhs);
    assertThat(lhs.compareTo(rhs)).isEqualTo(-1);
  }

  @Test
  public void compareTo_lessThan() throws Exception {
    CelSourceLocation lhs = CelSourceLocation.of(1, 1);
    CelSourceLocation rhs = CelSourceLocation.of(2, 2);
    assertThat(lhs.compareTo(rhs)).isEqualTo(-1);
  }

  @Test
  public void compareTo_greaterThanOrEqualTo() throws Exception {
    CelSourceLocation lhs = CelSourceLocation.of(1, 2);
    CelSourceLocation rhs = CelSourceLocation.of(1, 1);
    assertThat(lhs.compareTo(rhs)).isEqualTo(1);
  }
}
