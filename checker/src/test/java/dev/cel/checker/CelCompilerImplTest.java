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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.compiler.CelCompilerImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelCompilerImplTest {

  @Test
  public void toCompilerBuilder_isImmutable() {
    CelCompilerBuilder celCompilerBuilder = CelCompilerFactory.standardCelCompilerBuilder();
    CelCompilerImpl celCompiler = (CelCompilerImpl) celCompilerBuilder.build();
    celCompilerBuilder.addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration(
            "test", CelOverloadDecl.newGlobalOverload("test_id", SimpleType.INT)));

    CelCompilerImpl.Builder newCompilerBuilder =
        (CelCompilerImpl.Builder) celCompiler.toCompilerBuilder();

    assertThat(newCompilerBuilder).isNotEqualTo(celCompilerBuilder);
  }
}
