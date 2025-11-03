// Copyright 2024 Google LLC
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

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.expr.conformance.proto3.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CelResolvedOverload}. */
@RunWith(JUnit4.class)
public final class CelResolvedOverloadTest {

  CelResolvedOverload getIncrementIntOverload() {
    return CelResolvedOverload.of(
        "increment_int",
        (args) -> {
          Long arg = (Long) args[0];
          return arg + 1;
        },
        /* isStrict= */ true,
        Long.class);
  }

  @Test
  public void canHandle_matchingTypes_returnsTrue() {
    assertThat(getIncrementIntOverload().canHandle(new Object[] {1L})).isTrue();
  }

  @Test
  public void canHandle_nullMessageType_returnsTrue() {
    CelResolvedOverload overload =
        CelResolvedOverload.of(
            "identity", (args) -> args[0], /* isStrict= */ true, TestAllTypes.class);
    assertThat(overload.canHandle(new Object[] {null})).isTrue();
  }

  @Test
  public void canHandle_nullPrimitive_returnsFalse() {
    CelResolvedOverload overload =
        CelResolvedOverload.of("identity", (args) -> args[0], /* isStrict= */ true, Long.class);
    assertThat(overload.canHandle(new Object[] {null})).isFalse();
  }

  @Test
  public void canHandle_nonMatchingTypes_returnsFalse() {
    assertThat(getIncrementIntOverload().canHandle(new Object[] {1.0})).isFalse();
  }

  @Test
  public void canHandle_nonMatchingArgCount_returnsFalse() {
    assertThat(getIncrementIntOverload().canHandle(new Object[] {1L, 2L})).isFalse();
  }
}
