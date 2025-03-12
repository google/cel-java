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

import com.google.common.collect.ImmutableSet;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.CelEnvironment.CanonicalCelExtension;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
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
            ExtensionConfig.of("bindings"),
            ExtensionConfig.of("encoders"),
            ExtensionConfig.of("lists"),
            ExtensionConfig.of("math"),
            ExtensionConfig.of("optional"),
            ExtensionConfig.of("protos"),
            ExtensionConfig.of("sets"),
            ExtensionConfig.of("strings"));
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
}
