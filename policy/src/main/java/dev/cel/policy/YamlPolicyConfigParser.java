package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.policy.PolicyConfig.ExtensionConfig;
import dev.cel.policy.PolicyConfig.FunctionDecl;
import dev.cel.policy.PolicyConfig.OverloadDecl;
import dev.cel.policy.PolicyConfig.TypeDecl;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class YamlPolicyConfigParser {

  public static PolicyConfig parse(String content) {
    Map<String, Object> yamlMap = parseYamlSource(content);

    String name = (String) yamlMap.getOrDefault("name", "");
    String description = (String) yamlMap.getOrDefault("description", "");
    String container = (String) yamlMap.getOrDefault("container", "");
    ImmutableSet<ExtensionConfig> extensions = parseExtensions(yamlMap);
    ImmutableSet<FunctionDecl> functions = parseFunctions(yamlMap);

    return PolicyConfig.newBuilder()
        .setName(name)
        .setDescription(description)
        .setContainer(container)
        .setExtensions(extensions)
        .setFunctions(functions)
        .build();
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
          .setArguments(parseArguments(overloadMap))
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

  private static ImmutableList<TypeDecl> parseArguments(Map<String, Object> overloadMap) {
    List<Map<String, Object>> argumentList = getListOfMapsOrDefault(overloadMap, "args");
    return
        argumentList.stream()
            .map(YamlPolicyConfigParser::parseTypeDecl)
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

  private YamlPolicyConfigParser() {
  }
}
