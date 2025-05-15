// Copyright 2025 Google LLC
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

package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelLiteRuntime;
import dev.cel.runtime.CelLiteRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelLiteExtensionsTest {

  @Test
  public void addSetsExtensions() throws Exception {
    CelCompiler compiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.sets(CelOptions.DEFAULT))
            .build();
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .addLibraries(CelLiteExtensions.sets(CelOptions.DEFAULT))
            .build();
    CelAbstractSyntaxTree ast = compiler.compile("sets.contains([1, 1], [1])").getAst();

    boolean result = (boolean) runtime.createProgram(ast).eval();

    assertThat(result).isTrue();
  }
}
