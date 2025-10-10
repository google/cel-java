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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.CelEnvironment.CanonicalCelExtension;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironment.LibrarySubset;
import dev.cel.bundle.CelEnvironment.LibrarySubset.FunctionSelector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelEnvironmentTest {

  @Test
  public void newBuilder_defaults() {
    CelEnvironment environment = CelEnvironment.newBuilder().build();

    assertThat(environment.source()).isEmpty();
    assertThat(environment.name()).isEmpty();
    assertThat(environment.description()).isEmpty();
    assertThat(environment.container()).isEmpty();
    assertThat(environment.extensions()).isEmpty();
    assertThat(environment.variables()).isEmpty();
    assertThat(environment.functions()).isEmpty();
  }

  @Test
  public void extend_allExtensions() throws Exception {
    ImmutableSet<ExtensionConfig> extensionConfigs =
        ImmutableSet.of(
            ExtensionConfig.latest("bindings"),
            ExtensionConfig.latest("encoders"),
            ExtensionConfig.latest("lists"),
            ExtensionConfig.latest("math"),
            ExtensionConfig.latest("optional"),
            ExtensionConfig.latest("protos"),
            ExtensionConfig.latest("sets"),
            ExtensionConfig.latest("strings"),
            ExtensionConfig.latest("comprehensions"));
    CelEnvironment environment =
        CelEnvironment.newBuilder().addExtensions(extensionConfigs).build();

    Cel cel = environment.extend(CelFactory.standardCelBuilder().build(), CelOptions.DEFAULT);
    CelAbstractSyntaxTree ast =
        cel.compile(
                "cel.bind(x, 10, math.greatest([1,x])) < int(' 11  '.trim()) &&"
                    + " optional.none().orValue(true) && [].flatten() == []")
            .getAst();
    boolean result = (boolean) cel.createProgram(ast).eval();

    assertThat(extensionConfigs.size()).isEqualTo(CelEnvironment.CEL_EXTENSION_CONFIG_MAP.size());
    assertThat(extensionConfigs.size()).isEqualTo(CanonicalCelExtension.values().length);
    assertThat(result).isTrue();
  }

  @Test
  public void extensionVersion_specific() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder().addExtensions(ExtensionConfig.of("math", 1)).build();

    Cel cel = environment.extend(CelFactory.standardCelBuilder().build(), CelOptions.DEFAULT);
    CelAbstractSyntaxTree ast1 = cel.compile("math.abs(-4)").getAst();
    assertThat(cel.createProgram(ast1).eval()).isEqualTo(4);

    // Version 1 of the 'math' extension does not include sqrt
    assertThat(
            assertThrows(
                CelValidationException.class,
                () -> {
                  cel.compile("math.sqrt(4)").getAst();
                }))
        .hasMessageThat()
        .contains("undeclared reference to 'sqrt'");
  }

  @Test
  public void extensionVersion_latest() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder().addExtensions(ExtensionConfig.latest("math")).build();

    Cel cel = environment.extend(CelFactory.standardCelBuilder().build(), CelOptions.DEFAULT);
    CelAbstractSyntaxTree ast = cel.compile("math.sqrt(4)").getAst();
    double result = (double) cel.createProgram(ast).eval();
    assertThat(result).isEqualTo(2.0);
  }

  @Test
  public void extensionVersion_unsupportedVersion_throws() {
    CelEnvironment environment =
        CelEnvironment.newBuilder().addExtensions(ExtensionConfig.of("math", -5)).build();

    assertThat(
            assertThrows(
                CelEnvironmentException.class,
                () -> {
                  environment.extend(CelFactory.standardCelBuilder().build(), CelOptions.DEFAULT);
                }))
        .hasMessageThat()
        .contains("Unsupported 'math' extension version -5");
  }

  @Test
  public void stdlibSubset_bothIncludeExcludeSet_throws() {
    assertThat(
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    CelEnvironment.newBuilder()
                        .setStandardLibrarySubset(
                            LibrarySubset.newBuilder()
                                .setDisabled(false)
                                .setIncludedMacros(ImmutableSet.of("foo"))
                                .setExcludedMacros(ImmutableSet.of("bar"))
                                .build())
                        .build()))
        .hasMessageThat()
        .contains("cannot both include and exclude macros");

    assertThat(
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    CelEnvironment.newBuilder()
                        .setStandardLibrarySubset(
                            LibrarySubset.newBuilder()
                                .setDisabled(false)
                                .setIncludedFunctions(
                                    ImmutableSet.of(
                                        FunctionSelector.create("foo", ImmutableSet.of())))
                                .setExcludedFunctions(
                                    ImmutableSet.of(
                                        FunctionSelector.create("bar", ImmutableSet.of())))
                                .build())
                        .build()))
        .hasMessageThat()
        .contains("cannot both include and exclude functions");
  }

  @Test
  public void stdlibSubset_disabled() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(LibrarySubset.newBuilder().setDisabled(true).build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result = extendedCompiler.compile("1 != 2");
    assertThat(result.getErrorString()).contains("undeclared reference to '_!=_'");
  }

  @Test
  public void stdlibSubset_macrosDisabled() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder().setDisabled(false).setMacrosDisabled(true).build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result =
        extendedCompiler.compile("['hello', 'world'].exists(v, v == 'hello')");
    assertThat(result.getErrorString()).contains("undeclared reference to 'exists'");
  }

  @Test
  public void stdlibSubset_macrosIncluded() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(false)
                    .setIncludedMacros(ImmutableSet.of(CelStandardMacro.EXISTS.getFunction()))
                    .build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result =
        extendedCompiler.compile("['hello', 'world'].exists(v, v == 'hello')");
    assertThat(result.hasError()).isFalse();

    result = extendedCompiler.compile("['hello', 'world'].exists_one(v, v == 'hello')");
    assertThat(result.getErrorString()).contains("undeclared reference to 'exists_one'");
  }

  @Test
  public void stdlibSubset_macrosExcluded() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(false)
                    .setExcludedMacros(ImmutableSet.of(CelStandardMacro.EXISTS_ONE.getFunction()))
                    .build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result =
        extendedCompiler.compile("['hello', 'world'].exists(v, v == 'hello')");
    assertThat(result.hasError()).isFalse();

    result = extendedCompiler.compile("['hello', 'world'].exists_one(v, v == 'hello')");
    assertThat(result.getErrorString()).contains("undeclared reference to 'exists_one'");
  }

  @Test
  public void stdlibSubset_functionsIncluded() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(false)
                    .setIncludedFunctions(
                        ImmutableSet.of(
                            FunctionSelector.create("_==_", ImmutableSet.of()),
                            FunctionSelector.create("_!=_", ImmutableSet.of()),
                            FunctionSelector.create("_&&_", ImmutableSet.of())))
                    .build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result = extendedCompiler.compile("1 == 1 && 1 != 2");
    assertThat(result.hasError()).isFalse();

    result = extendedCompiler.compile("1 == 1 && 1 != 1 + 1");
    assertThat(result.getErrorString()).contains("undeclared reference to '_+_'");
  }

  @Test
  public void stdlibSubset_functionOverloadIncluded() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(false)
                    .setIncludedFunctions(
                        ImmutableSet.of(
                            FunctionSelector.create("_+_", ImmutableSet.of("add_int64"))))
                    .build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result = extendedCompiler.compile("1 + 2");
    assertThat(result.hasError()).isFalse();

    result = extendedCompiler.compile("1.0 + 2.0");
    assertThat(result.getErrorString())
        .contains("found no matching overload for '_+_' applied to '(double, double)'");
  }

  @Test
  public void stdlibSubset_functionsExcluded() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(false)
                    .setExcludedFunctions(
                        ImmutableSet.of(FunctionSelector.create("_+_", ImmutableSet.of())))
                    .build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result = extendedCompiler.compile("1 == 1 && 1 != 2");
    assertThat(result.hasError()).isFalse();

    result = extendedCompiler.compile("1 == 1 && 1 != 1 + 1");
    assertThat(result.getErrorString()).contains("undeclared reference to '_+_'");
  }

  @Test
  public void stdlibSubset_functionOverloadExcluded() throws Exception {
    CelEnvironment environment =
        CelEnvironment.newBuilder()
            .setStandardLibrarySubset(
                LibrarySubset.newBuilder()
                    .setDisabled(false)
                    .setExcludedFunctions(
                        ImmutableSet.of(
                            FunctionSelector.create("_+_", ImmutableSet.of("add_int64"))))
                    .build())
            .build();

    CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelCompiler extendedCompiler = environment.extend(compiler, CelOptions.DEFAULT);
    CelValidationResult result = extendedCompiler.compile("1 == 1 && 1 != 2");
    assertThat(result.hasError()).isFalse();

    result = extendedCompiler.compile("1 == 1 && 1 != 1 + 1");
    assertThat(result.getErrorString()).contains("found no matching overload for '_+_'");
  }
}
