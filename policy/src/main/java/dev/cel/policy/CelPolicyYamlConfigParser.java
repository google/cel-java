package dev.cel.policy;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static dev.cel.policy.YamlHelper.getListOfMapsOrDefault;
import static dev.cel.policy.YamlHelper.getListOfMapsOrThrow;
import static dev.cel.policy.YamlHelper.getMapOrThrow;
import static dev.cel.policy.YamlHelper.getOrThrow;

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

final class CelPolicyYamlConfigParser implements CelPolicyConfigParser {

  @Override
  public CelPolicyConfig parse(String content) throws CelPolicyValidationException {
    try {
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
    } catch (Exception e) {
      throw new CelPolicyValidationException(e.getMessage(), e);
    }
  }

  private static ImmutableSet<VariableDecl> parseVariables(Map<String, Object> yamlMap) {
    ImmutableSet.Builder<VariableDecl> variableSetBuilder = ImmutableSet.builder();
    List<Map<String, Object>> variableList = getListOfMapsOrDefault(yamlMap, "variables");
    for (Map<String, Object> variableMap : variableList) {
      variableSetBuilder.add(VariableDecl.create(
          getOrThrow(variableMap, "name", String.class),
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
          getOrThrow(functionMap, "name", String.class),
          parseOverloads(functionMap)
      ));
    }

    return functionSetBuilder.build();
  }

  private static ImmutableSet<OverloadDecl> parseOverloads(Map<String, Object> functionMap) {
    ImmutableSet.Builder<OverloadDecl> overloadSetBuilder = ImmutableSet.builder();
    List<Map<String, Object>> overloadList = getListOfMapsOrThrow(functionMap, "overloads");
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
      String name = getOrThrow(extensionMap, "name", String.class);
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

  private static Map<String, Object> parseYamlSource(String content) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    return yaml.load(content);
  }

  static CelPolicyYamlConfigParser newInstance() {
    return new CelPolicyYamlConfigParser();
  }

  private CelPolicyYamlConfigParser() {
  }
}
