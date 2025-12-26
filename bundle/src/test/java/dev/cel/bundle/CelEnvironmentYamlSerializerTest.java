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
import dev.cel.bundle.CelEnvironment.LibrarySubset;
import dev.cel.bundle.CelEnvironment.LibrarySubset.FunctionSelector;
import dev.cel.bundle.CelEnvironment.OverloadDecl;
import dev.cel.bundle.CelEnvironment.TypeDecl;
import dev.cel.bundle.CelEnvironment.VariableDecl;
import dev.cel.common.CelContainer;
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
            .setContainer(
                CelContainer.newBuilder()
                    .setName("test.container")
                    .addAbbreviations("abbr1.Abbr1", "abbr2.Abbr2")
                    .addAlias("alias1", "qual.name1")
                    .addAlias("alias2", "qual.name2")
                    .build())
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
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(true)
                    .setMacrosDisabled(true)
                    .setIncludedMacros(ImmutableSet.of("has", "exists"))
                    .setIncludedFunctions(
                        ImmutableSet.of(
                            FunctionSelector.create("_!=_", ImmutableSet.of()),
                            FunctionSelector.create(
                                "_+_", ImmutableSet.of("add_bytes", "add_list"))))
                    .build())
            .build();

    String yamlOutput = CelEnvironmentYamlSerializer.toYaml(environment);
    try {
      String yamlFileContent = readFile("environment/dump_env.yaml");
      // Strip the license at the beginning of the file
      String expectedOutput = yamlFileContent.replaceAll("#.*\\n", "").replaceAll("^\\n", "");
      assertThat(yamlOutput).isEqualTo(expectedOutput);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String readFile(String path) throws IOException {
    URL url = Resources.getResource(Ascii.toLowerCase(path));
    return Resources.toString(url, UTF_8);
  }

  @Test
  public void standardLibrary_excludeMacrosAndFunctions() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setName("dump_env")
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(false)
                    .setExcludedMacros(ImmutableSet.of("has", "exists"))
                    .setExcludedFunctions(
                        ImmutableSet.of(
                            FunctionSelector.create("_!=_", ImmutableSet.of()),
                            FunctionSelector.create(
                                "_+_", ImmutableSet.of("add_bytes", "add_list"))))
                    .build())
            .build();

    String yamlOutput = CelEnvironmentYamlSerializer.toYaml(environment);

    String expectedYaml =
        "name: dump_env\n"
            + "stdlib:\n"
            + "  exclude_macros:\n"
            + "  - exists\n"
            + "  - has\n"
            + "  exclude_functions:\n"
            + "  - name: _!=_\n"
            + "  - name: _+_\n"
            + "    overloads:\n"
            + "    - id: add_bytes\n"
            + "    - id: add_list\n";

    assertThat(yamlOutput).isEqualTo(expectedYaml);
  }
}
