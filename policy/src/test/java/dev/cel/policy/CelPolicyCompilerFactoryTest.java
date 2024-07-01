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

package dev.cel.policy;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelParserFactory;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyCompilerFactoryTest {

  @Test
  public void newPolicyCompiler_compilerRuntimeCombined() {
    assertThat(
            CelPolicyCompilerFactory.newPolicyCompiler(
                CelCompilerFactory.standardCelCompilerBuilder().build(),
                CelRuntimeFactory.standardCelRuntimeBuilder().build()))
        .isNotNull();
  }

  @Test
  public void newPolicyCompiler_parserCheckerRuntimeCombined() {
    assertThat(
            CelPolicyCompilerFactory.newPolicyCompiler(
                CelParserFactory.standardCelParserBuilder().build(),
                CelCompilerFactory.standardCelCheckerBuilder().build(),
                CelRuntimeFactory.standardCelRuntimeBuilder().build()))
        .isNotNull();
  }
}
