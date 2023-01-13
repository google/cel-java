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

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.rpc.context.AttributeContext;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import java.util.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelRuntimeTest {

  @Test
  public void evaluate_anyPackedEqualityUsingProtoDifferencer_success() throws Exception {
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setOptions(CelOptions.current().enableProtoDifferencerEquality(true).build())
            .addMessageTypes(AttributeContext.getDescriptor())
            .build();
    // Checked Expression for 'a == b' where a, b are google.rpc.context.AttributeContext message
    // types.
    // This will be removed in favor of compiling the expression inside this test once Cel Compiler
    // is OSSed.
    String base64EncodedCheckedExpr =
        "EgcIARIDCgFhEgcIAxIDCgFiEgwIAhIIGgZlcXVhbHMaKQgBEiVKI2dvb2dsZS5ycGMuY29udGV4dC5BdHRyaWJ1dGVDb250ZXh0GikIAxIlSiNnb29nbGUucnBjLmNvbnRleHQuQXR0cmlidXRlQ29udGV4dBoGCAISAhgBIhwQAjIYEgRfPT1fGgcQASIDCgFhGgcQAyIDCgFiKh4SBzxpbnB1dD4aAQciBAgBEAAiBAgCEAIiBAgDEAU=";
    CheckedExpr expr =
        CheckedExpr.parseFrom(
            Base64.getDecoder().decode(base64EncodedCheckedExpr),
            ExtensionRegistry.getEmptyRegistry());
    CelRuntime.Program program =
        celRuntime.createProgram(CelAbstractSyntaxTree.fromCheckedExpr(expr));

    Object evaluatedResult =
        program.eval(
            ImmutableMap.of(
                "a",
                AttributeContext.newBuilder()
                    .addExtensions(
                        Any.newBuilder()
                            .setTypeUrl("type.googleapis.com/google.rpc.context.AttributeContext")
                            .setValue(ByteString.copyFromUtf8("\032\000:\000"))
                            .build())
                    .build(),
                "b",
                AttributeContext.newBuilder()
                    .addExtensions(
                        Any.newBuilder()
                            .setTypeUrl("type.googleapis.com/google.rpc.context.AttributeContext")
                            .setValue(ByteString.copyFromUtf8(":\000\032\000"))
                            .build())
                    .build()));

    assertThat(evaluatedResult).isEqualTo(true);
  }
}
