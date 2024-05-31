package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.policy.CelPolicyConfig.ExtensionConfig;
import dev.cel.policy.CelPolicyConfig.FunctionDecl;
import dev.cel.policy.CelPolicyConfig.OverloadDecl;
import dev.cel.policy.CelPolicyConfig.TypeDecl;
import dev.cel.policy.CelPolicyConfig.VariableDecl;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class CelPolicyYamlConfigParser {

  public static CelPolicyConfig parse(String content) {
    Map<String, Object> yamlMap = parseYamlSource(content);

    String name = (String) yamlMap.getOrDefault("name", "");
    String description = (String) yamlMap.getOrDefault("description", "");
    String container = (String) yamlMap.getOrDefault("container", "");
    ImmutableSet<VariableDecl> variables = parseVariables(yamlMap);
    ImmutableSet<FunctionDecl> functions = parseFunctions(yamlMap);
    ImmutableSet<ExtensionConfig> extensions = parseExtensions(yamlMap);

    return CelPolicyConfig.newBuilder()
        .setName(name)
        .setDescription(description)
        .setContainer(container)
        .setVariables(variables)
        .setFunctions(functions)
        .setExtensions(extensions)
        .build();
  }

  private static ImmutableSet<VariableDecl> parseVariables(Map<String, Object> yamlMap) {
    ImmutableSet.Builder<VariableDecl> variableSetBuilder = ImmutableSet.builder();
    List<Map<String, Object>> variableList = getListOfMapsOrDefault(yamlMap, "variables");
    for (Map<String, Object> variableMap : variableList) {
      variableSetBuilder.add(VariableDecl.create(
          (String) variableMap.getOrDefault("name", ""),
          parseTypeDecl(getMapOrThrow(variableMap, "type"))
      ));
    }

    return variableSetBuilder.build();
  }

  private static ImmutableSet<FunctionDecl> parseFunctions(Map<String, Object> yamlMap) {
    ImmutableSet.Builder<FunctionDecl> functionSetBuilder = ImmutableSet.builder();
    List<Map<String, Object>> functionList = getListOfMapsOrDefault(yamlMap, "functions");
    for (Map<String, Object> functionMap : functionList) {
      functionSetBuilder.add(FunctionDecl.create(
          (String) functionMap.getOrDefault("name", ""),
          parseOverloads(functionMap)
      ));
    }

    return functionSetBuilder.build();
  }

  private static ImmutableSet<OverloadDecl> parseOverloads(Map<String, Object> functionMap) {
    ImmutableSet.Builder<OverloadDecl> overloadSetBuilder = ImmutableSet.builder();
    List<Map<String, Object>> overloadList = getListOfMapsOrDefault(functionMap,
        "overloads");
    for (Map<String, Object> overloadMap : overloadList) {
      OverloadDecl.Builder overloadDeclBuilder = OverloadDecl.newBuilder()
          .setId((String) overloadMap.getOrDefault("id", ""))
          .setArguments(parseOverloadArguments(overloadMap))
          .setReturnType(parseTypeDecl(
              getMapOrThrow(overloadMap, "return")));

      if (overloadMap.containsKey("target")) {
        overloadDeclBuilder.setTarget(
            parseTypeDecl(getMapOrThrow(overloadMap, "target")));
      }

      overloadSetBuilder.add(overloadDeclBuilder.build());
    }

    return overloadSetBuilder.build();
  }

  private static ImmutableSet<ExtensionConfig> parseExtensions(Map<String, Object> yamlMap) {
    ImmutableSet.Builder<ExtensionConfig> extensionConfigBuilder = ImmutableSet.builder();
    List<Map<String, Object>> extensionList = getListOfMapsOrDefault(yamlMap,
        "extensions");
    for (Map<String, Object> extensionMap : extensionList) {
      String name = (String) extensionMap.getOrDefault("name", "");
      int version = (int) extensionMap.getOrDefault("version", 0);

      extensionConfigBuilder.add(ExtensionConfig.of(name, version));
    }

    return extensionConfigBuilder.build();
  }

  private static ImmutableList<TypeDecl> parseOverloadArguments(Map<String, Object> overloadMap) {
    List<Map<String, Object>> argumentList = getListOfMapsOrDefault(overloadMap, "args");
    return
        argumentList.stream()
            .map(CelPolicyYamlConfigParser::parseTypeDecl)
            .collect(toImmutableList());
  }

  private static TypeDecl parseTypeDecl(Map<String, Object> typeMap) {
    TypeDecl.Builder builder = TypeDecl.newBuilder()
        .setName((String) typeMap.getOrDefault("type_name", ""))
        .setIsTypeParam((boolean) typeMap.getOrDefault("is_type_param", false));

    List<Map<String, Object>> paramsList = getListOfMapsOrDefault(typeMap, "params");
    for (Map<String, Object> paramMap : paramsList) {
      builder.addParams(parseTypeDecl(paramMap));
    }

    return builder.build();
  }

  private static Map<String, Object> getMapOrThrow(Map<String, Object> map, String key) {
    return (Map<String, Object>) checkNotNull(map.get(key));
  }

  private static List<Map<String, Object>> getListOfMapsOrDefault(Map<String, Object> map,
      String key) {
    return (List<Map<String, Object>>) map.getOrDefault(key, ImmutableList.of());
  }

  private static Map<String, Object> parseYamlSource(String content) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    return yaml.load(content);
  }

  private CelPolicyYamlConfigParser() {
  }
}
