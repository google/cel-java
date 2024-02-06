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

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelReferenceTest {

  @Test
  public void constructCelReference_withEmptyArguments() {
    CelReference reference = CelReference.newBuilder().setName("refName").build();

    assertThat(reference.name()).isEqualTo("refName");
    assertThat(reference.overloadIds()).isEmpty();
    assertThat(reference.value()).isEmpty();
  }

  @Test
  public void constructCelReference_withOverloadIds() {
    CelReference reference =
        CelReference.newBuilder().setName("refName").addOverloadIds("a", "b", "c").build();

    assertThat(reference.name()).isEqualTo("refName");
    assertThat(reference.overloadIds()).containsExactly("a", "b", "c");
    assertThat(reference.value()).isEmpty();
  }

  @Test
  public void constructCelReference_withValue() {
    CelReference reference =
        CelReference.newBuilder().setName("refName").setValue(CelConstant.ofValue(10)).build();

    assertThat(reference.name()).isEqualTo("refName");
    assertThat(reference.overloadIds()).isEmpty();
    assertThat(reference.value()).hasValue(CelConstant.ofValue(10));
  }

  @Test
  public void constructCelReference_bothArgumentsSet_throws() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CelReference.newBuilder()
                    .setName("refName")
                    .addOverloadIds(ImmutableList.of("a", "b", "c"))
                    .setValue(CelConstant.ofValue(10))
                    .build());

    assertThat(exception)
        .hasMessageThat()
        .contains("Value and overloadIds cannot be set at the same time.");
  }
}
