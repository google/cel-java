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
import dev.cel.expr.conformance.proto3.TestAllTypes;
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

  @Test
  public void toRuntimeBuilder_isNewInstance() {
    CelRuntimeBuilder celRuntimeBuilder = CelRuntimeFactory.standardCelRuntimeBuilder();
    CelRuntimeLegacyImpl celRuntime = (CelRuntimeLegacyImpl) celRuntimeBuilder.build();

    CelRuntimeLegacyImpl.Builder newRuntimeBuilder =
        (CelRuntimeLegacyImpl.Builder) celRuntime.toRuntimeBuilder();

    assertThat(newRuntimeBuilder).isNotEqualTo(celRuntimeBuilder);
  }

  @Test
  public void toRuntimeBuilder_isImmutable() {
    CelRuntimeBuilder originalRuntimeBuilder = CelRuntimeFactory.standardCelRuntimeBuilder();
    CelRuntimeLegacyImpl celRuntime = (CelRuntimeLegacyImpl) originalRuntimeBuilder.build();
    originalRuntimeBuilder.addLibraries(runtimeBuilder -> {});

    CelRuntimeLegacyImpl.Builder newRuntimeBuilder =
        (CelRuntimeLegacyImpl.Builder) celRuntime.toRuntimeBuilder();

    assertThat(newRuntimeBuilder.getRuntimeLibraries().build()).isEmpty();
  }

  @Test
  public void toRuntimeBuilder_collectionProperties_copied() {
    CelRuntimeBuilder celRuntimeBuilder = CelRuntimeFactory.standardCelRuntimeBuilder();
    celRuntimeBuilder.addMessageTypes(TestAllTypes.getDescriptor());
    celRuntimeBuilder.addFileTypes(TestAllTypes.getDescriptor().getFile());
    celRuntimeBuilder.addFunctionBindings(CelFunctionBinding.from("test", Integer.class, arg -> 1));
    celRuntimeBuilder.addLibraries(runtimeBuilder -> {});
    int originalFileTypesSize =
        ((CelRuntimeLegacyImpl.Builder) celRuntimeBuilder).getFileTypes().build().size();
    CelRuntimeLegacyImpl celRuntime = (CelRuntimeLegacyImpl) celRuntimeBuilder.build();

    CelRuntimeLegacyImpl.Builder newRuntimeBuilder =
        (CelRuntimeLegacyImpl.Builder) celRuntime.toRuntimeBuilder();

    assertThat(newRuntimeBuilder.getFunctionBindings()).hasSize(1);
    assertThat(newRuntimeBuilder.getRuntimeLibraries().build()).hasSize(1);
    assertThat(newRuntimeBuilder.getFileTypes().build()).hasSize(originalFileTypesSize);
  }

  @Test
  public void toRuntimeBuilder_collectionProperties_areImmutable() {
    CelRuntimeBuilder celRuntimeBuilder = CelRuntimeFactory.standardCelRuntimeBuilder();
    CelRuntimeLegacyImpl celRuntime = (CelRuntimeLegacyImpl) celRuntimeBuilder.build();
    CelRuntimeLegacyImpl.Builder newRuntimeBuilder =
        (CelRuntimeLegacyImpl.Builder) celRuntime.toRuntimeBuilder();

    // Mutate the original builder containing collections
    celRuntimeBuilder.addMessageTypes(TestAllTypes.getDescriptor());
    celRuntimeBuilder.addFileTypes(TestAllTypes.getDescriptor().getFile());
    celRuntimeBuilder.addFunctionBindings(CelFunctionBinding.from("test", Integer.class, arg -> 1));
    celRuntimeBuilder.addLibraries(runtimeBuilder -> {});

    assertThat(newRuntimeBuilder.getFunctionBindings()).isEmpty();
    assertThat(newRuntimeBuilder.getRuntimeLibraries().build()).isEmpty();
    assertThat(newRuntimeBuilder.getFileTypes().build()).isEmpty();
  }
}
