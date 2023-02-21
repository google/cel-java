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

import dev.cel.expr.CheckedExpr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.rpc.context.AttributeContext;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelProtoV1Alpha1AbstractSyntaxTree;
import dev.cel.common.types.CelV1AlphaTypes;
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
        celRuntime.createProgram(CelProtoAbstractSyntaxTree.fromCheckedExpr(expr).getAst());

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

  @Test
  public void evaluate_v1alpha1CheckedExpr() throws Exception {
    // Note: v1alpha1 proto support exists only to help migrate existing consumers.
    // New users of CEL should use the canonical protos instead (I.E: dev.cel.expr)
    com.google.api.expr.v1alpha1.CheckedExpr checkedExpr =
        com.google.api.expr.v1alpha1.CheckedExpr.newBuilder()
            .setExpr(
                Expr.newBuilder()
                    .setId(1)
                    .setConstExpr(Constant.newBuilder().setStringValue("Hello world!").build())
                    .build())
            .putTypeMap(1, CelV1AlphaTypes.create(PrimitiveType.STRING))
            .build();
    CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    CelRuntime.Program program =
        celRuntime.createProgram(
            CelProtoV1Alpha1AbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst());

    String evaluatedResult = (String) program.eval();

    assertThat(evaluatedResult).isEqualTo("Hello world!");
  }
}
