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

import com.google.common.testing.EqualsTester;
import dev.cel.common.ast.CelExpr;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelMacroTest {

  @Test
  public void testHas() {
    assertThat(CelMacro.HAS.getFunction()).isEqualTo(Operator.HAS.getFunction());
    assertThat(CelMacro.HAS.getArgumentCount()).isEqualTo(1);
    assertThat(CelMacro.HAS.isReceiverStyle()).isFalse();
    assertThat(CelMacro.HAS.getKey()).isEqualTo("has:1:false");
    assertThat(CelMacro.HAS.isVariadic()).isFalse();
    assertThat(CelMacro.HAS.toString()).isEqualTo(CelMacro.HAS.getKey());
    assertThat(CelMacro.HAS.hashCode()).isEqualTo(CelMacro.HAS.getKey().hashCode());
    assertThat(CelMacro.HAS).isEquivalentAccordingToCompareTo(CelMacro.HAS);
  }

  @Test
  public void testAll() {
    assertThat(CelMacro.ALL.getFunction()).isEqualTo(Operator.ALL.getFunction());
    assertThat(CelMacro.ALL.getArgumentCount()).isEqualTo(2);
    assertThat(CelMacro.ALL.isReceiverStyle()).isTrue();
    assertThat(CelMacro.ALL.getKey()).isEqualTo("all:2:true");
    assertThat(CelMacro.ALL.isVariadic()).isFalse();
    assertThat(CelMacro.ALL.toString()).isEqualTo(CelMacro.ALL.getKey());
    assertThat(CelMacro.ALL.hashCode()).isEqualTo(CelMacro.ALL.getKey().hashCode());
    assertThat(CelMacro.ALL).isEquivalentAccordingToCompareTo(CelMacro.ALL);
  }

  @Test
  public void testExists() {
    assertThat(CelMacro.EXISTS.getFunction()).isEqualTo(Operator.EXISTS.getFunction());
    assertThat(CelMacro.EXISTS.getArgumentCount()).isEqualTo(2);
    assertThat(CelMacro.EXISTS.isReceiverStyle()).isTrue();
    assertThat(CelMacro.EXISTS.getKey()).isEqualTo("exists:2:true");
    assertThat(CelMacro.EXISTS.isVariadic()).isFalse();
    assertThat(CelMacro.EXISTS.toString()).isEqualTo(CelMacro.EXISTS.getKey());
    assertThat(CelMacro.EXISTS.hashCode()).isEqualTo(CelMacro.EXISTS.getKey().hashCode());
    assertThat(CelMacro.EXISTS).isEquivalentAccordingToCompareTo(CelMacro.EXISTS);
  }

  @Test
  public void testExistsOne() {
    assertThat(CelMacro.EXISTS_ONE.getFunction()).isEqualTo(Operator.EXISTS_ONE.getFunction());
    assertThat(CelMacro.EXISTS_ONE.getArgumentCount()).isEqualTo(2);
    assertThat(CelMacro.EXISTS_ONE.isReceiverStyle()).isTrue();
    assertThat(CelMacro.EXISTS_ONE.getKey()).isEqualTo("exists_one:2:true");
    assertThat(CelMacro.EXISTS_ONE.isVariadic()).isFalse();
    assertThat(CelMacro.EXISTS_ONE.toString()).isEqualTo(CelMacro.EXISTS_ONE.getKey());
    assertThat(CelMacro.EXISTS_ONE.hashCode()).isEqualTo(CelMacro.EXISTS_ONE.getKey().hashCode());
    assertThat(CelMacro.EXISTS_ONE).isEquivalentAccordingToCompareTo(CelMacro.EXISTS_ONE);
  }

  @Test
  public void testMap2() {
    assertThat(CelMacro.MAP.getFunction()).isEqualTo(Operator.MAP.getFunction());
    assertThat(CelMacro.MAP.getArgumentCount()).isEqualTo(2);
    assertThat(CelMacro.MAP.isReceiverStyle()).isTrue();
    assertThat(CelMacro.MAP.getKey()).isEqualTo("map:2:true");
    assertThat(CelMacro.MAP.isVariadic()).isFalse();
    assertThat(CelMacro.MAP.toString()).isEqualTo(CelMacro.MAP.getKey());
    assertThat(CelMacro.MAP.hashCode()).isEqualTo(CelMacro.MAP.getKey().hashCode());
    assertThat(CelMacro.MAP).isEquivalentAccordingToCompareTo(CelMacro.MAP);
  }

  @Test
  public void testMap3() {
    assertThat(CelMacro.MAP_FILTER.getFunction()).isEqualTo(Operator.MAP.getFunction());
    assertThat(CelMacro.MAP_FILTER.getArgumentCount()).isEqualTo(3);
    assertThat(CelMacro.MAP_FILTER.isReceiverStyle()).isTrue();
    assertThat(CelMacro.MAP_FILTER.getKey()).isEqualTo("map:3:true");
    assertThat(CelMacro.MAP_FILTER.isVariadic()).isFalse();
    assertThat(CelMacro.MAP_FILTER.toString()).isEqualTo(CelMacro.MAP_FILTER.getKey());
    assertThat(CelMacro.MAP_FILTER.hashCode()).isEqualTo(CelMacro.MAP_FILTER.getKey().hashCode());
    assertThat(CelMacro.MAP_FILTER).isEquivalentAccordingToCompareTo(CelMacro.MAP_FILTER);
  }

  @Test
  public void testFilter() {
    assertThat(CelMacro.FILTER.getFunction()).isEqualTo(Operator.FILTER.getFunction());
    assertThat(CelMacro.FILTER.getArgumentCount()).isEqualTo(2);
    assertThat(CelMacro.FILTER.isReceiverStyle()).isTrue();
    assertThat(CelMacro.FILTER.getKey()).isEqualTo("filter:2:true");
    assertThat(CelMacro.FILTER.isVariadic()).isFalse();
    assertThat(CelMacro.FILTER.toString()).isEqualTo(CelMacro.FILTER.getKey());
    assertThat(CelMacro.FILTER.hashCode()).isEqualTo(CelMacro.FILTER.getKey().hashCode());
    assertThat(CelMacro.FILTER).isEquivalentAccordingToCompareTo(CelMacro.FILTER);
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(CelMacro.HAS)
        .addEqualityGroup(CelMacro.ALL)
        .addEqualityGroup(CelMacro.EXISTS)
        .addEqualityGroup(CelMacro.EXISTS_ONE)
        .addEqualityGroup(CelMacro.MAP)
        .addEqualityGroup(CelMacro.MAP_FILTER)
        .addEqualityGroup(CelMacro.FILTER)
        .testEquals();
  }

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
