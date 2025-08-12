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
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link CelExtensions#comprehensions()} */
@RunWith(TestParameterInjector.class)
public class CelComprehensionsExtensionsTest {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addLibraries(CelExtensions.comprehensions())
          .build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          // .addLibraries(CelExtensions.comprehensions())
          .build();

  @Test
  public void all_comprehension_success() throws Exception {
    CelAbstractSyntaxTree ast =
        CEL_COMPILER.compile("[4,5,6].all(x, s, x > 3)").getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(true);
  }
}
