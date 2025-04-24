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

package dev.cel.bundle;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironment.FunctionDecl;
import dev.cel.bundle.CelEnvironment.OverloadDecl;
import dev.cel.bundle.CelEnvironment.TypeDecl;
import dev.cel.bundle.CelEnvironment.VariableDecl;
import java.io.IOException;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelEnvironmentYamlSerializerTest {

  @Test
  public void toYaml_success() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setName("dump_env")
            .setDescription("dump_env description")
            .setContainer("test.container")
            .addExtensions(
                ImmutableSet.of(
                    ExtensionConfig.of("bindings"),
                    ExtensionConfig.of("encoders"),
                    ExtensionConfig.of("lists"),
                    ExtensionConfig.of("math"),
                    ExtensionConfig.of("optional"),
                    ExtensionConfig.of("protos"),
                    ExtensionConfig.of("sets"),
                    ExtensionConfig.of("strings", 1)))
            .setVariables(
                ImmutableSet.of(
                    VariableDecl.create(
                        "request", TypeDecl.create("google.rpc.context.AttributeContext.Request")),
                    VariableDecl.create(
                        "map_var",
                        TypeDecl.newBuilder()
                            .setName("map")
                            .addParams(TypeDecl.create("string"))
                            .addParams(TypeDecl.create("string"))
                            .build())))
            .setFunctions(
                ImmutableSet.of(
                    FunctionDecl.create(
                        "getOrDefault",
                        ImmutableSet.of(
                            OverloadDecl.newBuilder()
                                .setId("getOrDefault_key_value")
                                .setTarget(
                                    TypeDecl.newBuilder()
                                        .setName("map")
                                        .addParams(
                                            TypeDecl.newBuilder()
                                                .setName("K")
                                                .setIsTypeParam(true)
                                                .build())
                                        .addParams(
                                            TypeDecl.newBuilder()
                                                .setName("V")
                                                .setIsTypeParam(true)
                                                .build())
                                        .build())
                                .setArguments(
                                    ImmutableList.of(
                                        TypeDecl.newBuilder()
                                            .setName("K")
                                            .setIsTypeParam(true)
                                            .build(),
                                        TypeDecl.newBuilder()
                                            .setName("V")
                                            .setIsTypeParam(true)
                                            .build()))
                                .setReturnType(
                                    TypeDecl.newBuilder().setName("V").setIsTypeParam(true).build())
                                .build())),
                    FunctionDecl.create(
                        "coalesce",
                        ImmutableSet.of(
                            OverloadDecl.newBuilder()
                                .setId("coalesce_null_int")
                                .setTarget(TypeDecl.create("google.protobuf.Int64Value"))
                                .setArguments(ImmutableList.of(TypeDecl.create("int")))
                                .setReturnType(TypeDecl.create("int"))
                                .build()))))
            .build();

    String yamlOutput = CelEnvironmentYamlSerializer.toYaml(environment);
    try {
      String yamlFileContent = readFile("environment/dump_env.yaml");
      assertThat(yamlFileContent).endsWith(yamlOutput);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String readFile(String path) throws IOException {
    URL url = Resources.getResource(Ascii.toLowerCase(path));
    return Resources.toString(url, UTF_8);
  }
}
