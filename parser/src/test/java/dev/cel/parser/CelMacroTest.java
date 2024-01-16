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

package dev.cel.parser;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.common.ast.CelExpr;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelMacroTest {

  @Test
  public void testGlobalVarArgMacro() {
    CelMacro macro =
        CelMacro.newGlobalVarArgMacro(
            "foo", (sourceFactory, target, arguments) -> Optional.of(CelExpr.newBuilder().build()));
    assertThat(macro.getFunction()).isEqualTo("foo");
    assertThat(macro.getArgumentCount()).isEqualTo(0);
    assertThat(macro.isReceiverStyle()).isFalse();
    assertThat(macro.getKey()).isEqualTo("foo:*:false");
    assertThat(macro.isVariadic()).isTrue();
    assertThat(macro.toString()).isEqualTo(macro.getKey());
    assertThat(macro.hashCode()).isEqualTo(macro.getKey().hashCode());
    assertThat(macro).isEquivalentAccordingToCompareTo(macro);
  }

  @Test
  public void testReceiverVarArgMacro() {
    CelMacro macro =
        CelMacro.newReceiverVarArgMacro(
            "foo", (sourceFactory, target, arguments) -> Optional.of(CelExpr.newBuilder().build()));
    assertThat(macro.getFunction()).isEqualTo("foo");
    assertThat(macro.getArgumentCount()).isEqualTo(0);
    assertThat(macro.isReceiverStyle()).isTrue();
    assertThat(macro.getKey()).isEqualTo("foo:*:true");
    assertThat(macro.isVariadic()).isTrue();
    assertThat(macro.toString()).isEqualTo(macro.getKey());
    assertThat(macro.hashCode()).isEqualTo(macro.getKey().hashCode());
    assertThat(macro).isEquivalentAccordingToCompareTo(macro);
  }
}
