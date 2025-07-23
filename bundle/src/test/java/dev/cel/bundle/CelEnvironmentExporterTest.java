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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironment.FunctionDecl;
import dev.cel.bundle.CelEnvironment.LibrarySubset;
import dev.cel.bundle.CelEnvironment.LibrarySubset.FunctionSelector;
import dev.cel.bundle.CelEnvironment.LibrarySubset.OverloadSelector;
import dev.cel.bundle.CelEnvironment.OverloadDecl;
import dev.cel.bundle.CelEnvironment.TypeDecl;
import dev.cel.bundle.CelEnvironment.VariableDecl;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.types.OpaqueType;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelExtensions;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelEnvironmentExporterTest {

  public static final CelOptions CEL_OPTIONS =
      CelOptions.newBuilder().enableHeterogeneousNumericComparisons(true).build();

  @Test
  public void extensions_latest() {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addCompilerLibraries(CelExtensions.math(CEL_OPTIONS, 2))
            .build();

    CelEnvironment celEnvironment =
        CelEnvironmentExporter.newBuilder().addStandardExtensions().build().export(cel);

    assertThat(celEnvironment.extensions())
        .containsExactly(ExtensionConfig.newBuilder().setName("math").setVersion(2).build());
  }

  @Test
  public void extensions_earlierVersion() {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addCompilerLibraries(CelExtensions.math(CEL_OPTIONS, 1))
            .build();

    CelEnvironment celEnvironment =
        CelEnvironmentExporter.newBuilder().addStandardExtensions().build().export(cel);

    assertThat(celEnvironment.extensions())
        .containsExactly(ExtensionConfig.newBuilder().setName("math").setVersion(1).build());
  }

  @Test
  public void standardLibrarySubset_favorExclusion() throws Exception {
    URL url = Resources.getResource("environment/subset_env.yaml");
    String yamlFileContent = Resources.toString(url, UTF_8);
    CelEnvironment environment = CelEnvironmentYamlParser.newInstance().parse(yamlFileContent);

    Cel standardCel =
        CelFactory.standardCelBuilder()
            .addCompilerLibraries(CelExtensions.math(CEL_OPTIONS, 2))
            .build();
    Cel extendedCel = environment.extend(standardCel, CEL_OPTIONS);

    CelEnvironment celEnvironment =
        CelEnvironmentExporter.newBuilder()
            .setMaxExcludedStandardFunctions(100)
            .setMaxExcludedStandardFunctionOverloads(100)
            .build()
            .export(extendedCel);

    assertThat(celEnvironment.standardLibrarySubset())
        .hasValue(
            LibrarySubset.newBuilder()
                .setDisabled(false)
                .setExcludedFunctions(
                    ImmutableSet.of(
                        FunctionSelector.create(
                            "_+_", ImmutableSet.of("add_bytes", "add_list", "add_string")),
                        FunctionSelector.create("duration", ImmutableSet.of("string_to_duration")),
                        FunctionSelector.create("matches", ImmutableSet.of()),
                        FunctionSelector.create(
                            "timestamp", ImmutableSet.of("string_to_timestamp"))))
                .setExcludedMacros(ImmutableSet.of("map", "filter"))
                .build());
  }

  @Test
  public void standardLibrarySubset_favorInclusion() throws Exception {
    URL url = Resources.getResource("environment/subset_env.yaml");
    String yamlFileContent = Resources.toString(url, UTF_8);
    CelEnvironment environment = CelEnvironmentYamlParser.newInstance().parse(yamlFileContent);

    Cel standardCel =
        CelFactory.standardCelBuilder()
            .addCompilerLibraries(CelExtensions.math(CEL_OPTIONS, 2))
            .build();
    Cel extendedCel = environment.extend(standardCel, CEL_OPTIONS);

    CelEnvironment celEnvironment =
        CelEnvironmentExporter.newBuilder()
            .setMaxExcludedStandardFunctions(0)
            .setMaxExcludedStandardFunctionOverloads(0)
            .build()
            .export(extendedCel);

    LibrarySubset actual = celEnvironment.standardLibrarySubset().get();

    // "matches" is fully excluded
    assertThat(actual.includedFunctions().stream().map(FunctionSelector::name))
        .doesNotContain("matches");

    // A subset of overloads is included. Note the absence of string_to_timestamp
    assertThat(actual.includedFunctions())
        .contains(
            FunctionSelector.create(
                "timestamp", ImmutableSet.of("int64_to_timestamp", "timestamp_to_timestamp")));

    Set<String> additionOverloads =
        actual.includedFunctions().stream()
            .filter(fs -> fs.name().equals("_+_"))
            .flatMap(fs -> fs.overloads().stream())
            .map(OverloadSelector::id)
            .collect(toCollection(HashSet::new));
    assertThat(additionOverloads).containsNoneOf("add_bytes", "add_list", "add_string");

    assertThat(actual.includedMacros()).containsNoneOf("map", "filter");

    // Random-check a few standard overloads
    assertThat(additionOverloads).containsAtLeast("add_int64", "add_uint64", "add_double");

    // Random-check a couple of standard functions
    assertThat(actual.includedFunctions())
        .contains(FunctionSelector.create("-_", ImmutableSet.of()));
    assertThat(actual.includedFunctions())
        .contains(FunctionSelector.create("getDayOfYear", ImmutableSet.of()));
    assertThat(actual.includedMacros()).containsAtLeast("all", "exists", "exists_one", "has");
  }

  @Test
  public void customFunctions() {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addCompilerLibraries(CelExtensions.math(CEL_OPTIONS, 1))
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "math.isFinite",
                    CelOverloadDecl.newGlobalOverload(
                        "math_isFinite_int64", SimpleType.BOOL, SimpleType.INT)),
                CelFunctionDecl.newFunctionDeclaration(
                    "addWeeks",
                    CelOverloadDecl.newMemberOverload(
                        "timestamp_addWeeks",
                        SimpleType.BOOL,
                        SimpleType.TIMESTAMP,
                        SimpleType.INT)))
            .build();

    CelEnvironmentExporter exporter =
        CelEnvironmentExporter.newBuilder().addStandardExtensions().build();
    CelEnvironment celEnvironment = exporter.export(cel);

    assertThat(celEnvironment.functions())
        .containsAtLeast(
            FunctionDecl.create(
                "math.isFinite",
                ImmutableSet.of(
                    OverloadDecl.newBuilder()
                        .setId("math_isFinite_int64")
                        .setArguments(ImmutableList.of(TypeDecl.create("int")))
                        .setReturnType(TypeDecl.create("bool"))
                        .build())),
            FunctionDecl.create(
                "addWeeks",
                ImmutableSet.of(
                    OverloadDecl.newBuilder()
                        .setId("timestamp_addWeeks")
                        .setTarget(TypeDecl.create("google.protobuf.Timestamp"))
                        .setArguments(ImmutableList.of(TypeDecl.create("int")))
                        .setReturnType(TypeDecl.create("bool"))
                        .build())));

    // Random-check some standard functions: we don't want to see them explicitly defined.
    assertThat(
            celEnvironment.functions().stream().map(FunctionDecl::name).collect(toImmutableList()))
        .containsNoneOf("_+_", "math.abs", "_in_", "__not_strictly_false__");
  }

  @Test
  public void customVariables() {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVarDeclarations(
                CelVarDecl.newVarDeclaration("x", SimpleType.INT),
                CelVarDecl.newVarDeclaration("y", OpaqueType.create("foo.Bar")))
            .build();

    CelEnvironmentExporter exporter =
        CelEnvironmentExporter.newBuilder().addStandardExtensions().build();
    CelEnvironment celEnvironment = exporter.export(cel);

    assertThat(celEnvironment.variables())
        .containsAtLeast(
            VariableDecl.create("x", TypeDecl.create("int")),
            VariableDecl.create("y", TypeDecl.create("foo.Bar")));

    // Random-check some standard variables: we don't want to see them explicitly included in
    // the CelEnvironment.
    assertThat(
            celEnvironment.variables().stream().map(VariableDecl::name).collect(toImmutableList()))
        .containsNoneOf("double", "null_type");
  }
}

