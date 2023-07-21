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

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.common.CelException;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelRuntimeLegacyImplTest {

  @Test
  public void evalException() throws CelException {
    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    CelRuntime.Program program = runtime.createProgram(compiler.compile("1/0").getAst());
    CelEvaluationException e = Assert.assertThrows(CelEvaluationException.class, program::eval);
    assertThat(e).hasCauseThat().isInstanceOf(ArithmeticException.class);
  }
}
