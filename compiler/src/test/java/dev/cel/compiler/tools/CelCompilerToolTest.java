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

package dev.cel.compiler.tools;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.testing.compiled.CompiledExprUtils.readCheckedExpr;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.StringValue;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelCompilerToolTest {
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addFunctionBindings(
              CelFunctionBinding.from("wrapper_string_isEmpty", String.class, String::isEmpty))
          .addLibraries(
              CelExtensions.encoders(),
              CelExtensions.math(CelOptions.DEFAULT),
              CelExtensions.lists(),
              CelExtensions.strings(),
              CelOptionalLibrary.INSTANCE)
          .addMessageTypes(TestAllTypes.getDescriptor())
          .build();

  @Test
  public void compiledCheckedExpr_string() throws Exception {
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_hello_world");

    String result = (String) CEL_RUNTIME.createProgram(ast).eval();
    assertThat(result).isEqualTo("hello world");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void compiledCheckedExpr_comprehension() throws Exception {
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_comprehension");

    List<Long> result = (List<Long>) CEL_RUNTIME.createProgram(ast).eval();
    assertThat(result).containsExactly(2L, 3L, 4L).inOrder();
  }

  @Test
  public void compiledCheckedExpr_protoMessage() throws Exception {
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_proto_message");

    TestAllTypes result = (TestAllTypes) CEL_RUNTIME.createProgram(ast).eval();
    assertThat(result).isEqualTo(TestAllTypes.newBuilder().setSingleInt32(1).build());
  }

  @Test
  public void compiledCheckedExpr_extensions() throws Exception {
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_extensions");

    assertThat(CEL_RUNTIME.createProgram(ast).eval()).isEqualTo(true);
  }

  @Test
  public void compiledCheckedExpr_extended_env() throws Exception {
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_extended_env");

    boolean result =
        (boolean)
            CEL_RUNTIME
                .createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "msg",
                        TestAllTypes.newBuilder()
                            .setSingleStringWrapper(StringValue.of("foo"))
                            .build()));

    assertThat(result).isTrue();
  }
}
