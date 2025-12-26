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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.cel.bundle.CelEnvironment.Alias;
import dev.cel.bundle.CelEnvironment.LibrarySubset;
import dev.cel.bundle.CelEnvironment.LibrarySubset.FunctionSelector;
import dev.cel.bundle.CelEnvironment.LibrarySubset.OverloadSelector;
import dev.cel.common.CelContainer;
import java.util.Comparator;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

/** Serializes a CelEnvironment into a YAML file. */
public final class CelEnvironmentYamlSerializer extends Representer {

  private static DumperOptions initDumperOptions() {
    DumperOptions options = new DumperOptions();
    options.setIndent(2);
    options.setPrettyFlow(true);
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    return options;
  }

  private static final DumperOptions YAML_OPTIONS = initDumperOptions();

  private static final CelEnvironmentYamlSerializer INSTANCE = new CelEnvironmentYamlSerializer();

  private CelEnvironmentYamlSerializer() {
    super(YAML_OPTIONS);
    this.multiRepresenters.put(CelEnvironment.class, new RepresentCelEnvironment());
    this.multiRepresenters.put(CelEnvironment.VariableDecl.class, new RepresentVariableDecl());
    this.multiRepresenters.put(CelEnvironment.FunctionDecl.class, new RepresentFunctionDecl());
    this.multiRepresenters.put(CelEnvironment.OverloadDecl.class, new RepresentOverloadDecl());
    this.multiRepresenters.put(CelEnvironment.TypeDecl.class, new RepresentTypeDecl());
    this.multiRepresenters.put(
        CelEnvironment.ExtensionConfig.class, new RepresentExtensionConfig());
    this.multiRepresenters.put(CelEnvironment.LibrarySubset.class, new RepresentLibrarySubset());
    this.multiRepresenters.put(
        CelEnvironment.LibrarySubset.FunctionSelector.class, new RepresentFunctionSelector());
    this.multiRepresenters.put(
        CelEnvironment.LibrarySubset.OverloadSelector.class, new RepresentOverloadSelector());
    this.multiRepresenters.put(CelEnvironment.Alias.class, new RepresentAlias());
    this.multiRepresenters.put(CelContainer.class, new RepresentContainer());
  }

  public static String toYaml(CelEnvironment environment) {
    // Yaml is not thread-safe, so we create a new instance for each serialization.
    Yaml yaml = new Yaml(INSTANCE, YAML_OPTIONS);
    return yaml.dump(environment);
  }

  private final class RepresentCelEnvironment implements Represent {
    @Override
    public Node representData(Object data) {
      CelEnvironment environment = (CelEnvironment) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("name", environment.name());
      if (!environment.description().isEmpty()) {
        configMap.put("description", environment.description());
      }
      if (!environment.container().name().isEmpty()
          || !environment.container().abbreviations().isEmpty()
          || !environment.container().aliases().isEmpty()) {
        configMap.put("container", environment.container());
      }
      if (!environment.extensions().isEmpty()) {
        configMap.put("extensions", environment.extensions().asList());
      }
      if (!environment.variables().isEmpty()) {
        configMap.put("variables", environment.variables().asList());
      }
      if (!environment.functions().isEmpty()) {
        configMap.put("functions", environment.functions().asList());
      }
      if (environment.standardLibrarySubset().isPresent()) {
        configMap.put("stdlib", environment.standardLibrarySubset().get());
      }
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentContainer implements Represent {

    @Override
    public Node representData(Object data) {
      CelContainer container = (CelContainer) data;
      ImmutableMap.Builder<String, Object> configMap = ImmutableMap.builder();
      if (!container.name().isEmpty()) {
        configMap.put("name", container.name());
      }
      if (!container.abbreviations().isEmpty()) {
        configMap.put("abbreviations", container.abbreviations());
      }
      if (!container.aliases().isEmpty()) {
        ImmutableList.Builder<Alias> aliases = ImmutableList.builder();
        for (Map.Entry<String, String> entry : container.aliases().entrySet()) {
          aliases.add(
              Alias.newBuilder()
                  .setAlias(entry.getKey())
                  .setQualifiedName(entry.getValue())
                  .build());
        }
        configMap.put("aliases", aliases.build());
      }
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentAlias implements Represent {

    @Override
    public Node representData(Object data) {
      Alias alias = (Alias) data;
      return represent(
          ImmutableMap.of("alias", alias.alias(), "qualified_name", alias.qualifiedName()));
    }
  }

  private final class RepresentExtensionConfig implements Represent {
    @Override
    public Node representData(Object data) {
      CelEnvironment.ExtensionConfig extension = (CelEnvironment.ExtensionConfig) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("name", extension.name());
      if (extension.version() > 0 && extension.version() != Integer.MAX_VALUE) {
        configMap.put("version", extension.version());
      }
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentVariableDecl implements Represent {
    @Override
    public Node representData(Object data) {
      CelEnvironment.VariableDecl variable = (CelEnvironment.VariableDecl) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("name", variable.name()).put("type_name", variable.type().name());
      if (!variable.type().params().isEmpty()) {
        configMap.put("params", variable.type().params());
      }
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentFunctionDecl implements Represent {
    @Override
    public Node representData(Object data) {
      CelEnvironment.FunctionDecl function = (CelEnvironment.FunctionDecl) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("name", function.name()).put("overloads", function.overloads().asList());
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentOverloadDecl implements Represent {
    @Override
    public Node representData(Object data) {
      CelEnvironment.OverloadDecl overload = (CelEnvironment.OverloadDecl) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("id", overload.id());
      if (overload.target().isPresent()) {
        configMap.put("target", overload.target().get());
      }
      configMap.put("args", overload.arguments()).put("return", overload.returnType());
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentTypeDecl implements Represent {
    @Override
    public Node representData(Object data) {
      CelEnvironment.TypeDecl type = (CelEnvironment.TypeDecl) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("type_name", type.name());
      if (!type.params().isEmpty()) {
        configMap.put("params", type.params());
      }
      if (type.isTypeParam()) {
        configMap.put("is_type_param", type.isTypeParam());
      }
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentLibrarySubset implements Represent {
    @Override
    public Node representData(Object data) {
      LibrarySubset librarySubset = (LibrarySubset) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      if (librarySubset.disabled()) {
        configMap.put("disabled", true);
      }
      if (librarySubset.macrosDisabled()) {
        configMap.put("disable_macros", true);
      }
      if (!librarySubset.includedMacros().isEmpty()) {
        configMap.put("include_macros", ImmutableList.sortedCopyOf(librarySubset.includedMacros()));
      }
      if (!librarySubset.excludedMacros().isEmpty()) {
        configMap.put("exclude_macros", ImmutableList.sortedCopyOf(librarySubset.excludedMacros()));
      }
      if (!librarySubset.includedFunctions().isEmpty()) {
        configMap.put(
            "include_functions",
            ImmutableList.sortedCopyOf(
                Comparator.comparing(FunctionSelector::name), librarySubset.includedFunctions()));
      }
      if (!librarySubset.excludedFunctions().isEmpty()) {
        configMap.put(
            "exclude_functions",
            ImmutableList.sortedCopyOf(
                Comparator.comparing(FunctionSelector::name), librarySubset.excludedFunctions()));
      }
      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentFunctionSelector implements Represent {
    @Override
    public Node representData(Object data) {
      FunctionSelector functionSelector = (FunctionSelector) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("name", functionSelector.name());
      if (!functionSelector.overloads().isEmpty()) {
        configMap.put(
            "overloads",
            ImmutableList.sortedCopyOf(
                Comparator.comparing(OverloadSelector::id), functionSelector.overloads()));
      }

      return represent(configMap.buildOrThrow());
    }
  }

  private final class RepresentOverloadSelector implements Represent {
    @Override
    public Node representData(Object data) {
      OverloadSelector overloadSelector = (OverloadSelector) data;
      return represent(ImmutableMap.<String, Object>of("id", overloadSelector.id()));
    }
  }
}
