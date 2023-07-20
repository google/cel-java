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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.common.ast.CelExpr;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelMacro;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelCompilerFactoryTest {

  @Test
  public void standardCelCheckerBuilder() {
    assertThat(CelCompilerFactory.standardCelCheckerBuilder().build()).isNotNull();
  }

  @Test
  public void standardCelCompilerBuilder() {
    assertThat(CelCompilerFactory.standardCelCompilerBuilder().build()).isNotNull();
  }

  @Test
  public void addCustomMacros_success() {
    assertThat(
            CelCompilerFactory.standardCelCompilerBuilder()
                .addMacros(
                    CelMacro.newReceiverMacro(
                        "dummy", 0, (a, b, c) -> Optional.of(CelExpr.newBuilder().build())))
                .build())
        .isNotNull();
  }
}
