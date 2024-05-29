package dev.cel.policy;

import com.google.common.collect.ImmutableSet;
import dev.cel.policy.PolicyConfig.ExtensionConfig;
import dev.cel.policy.PolicyConfig.FunctionDecl;
import dev.cel.policy.PolicyConfig.OverloadDecl;
import dev.cel.policy.PolicyConfig.TypeDecl;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.List;
import java.util.Map;

public final class YamlPolicyConfigParser {

  public static PolicyConfig parse(String content) {
    Map<String, Object> yamlMap = parseYamlSource(content);

    String name = getStringOrDefault(yamlMap, "name");
    String description = getStringOrDefault(yamlMap, "description");
    String container = getStringOrDefault(yamlMap, "container");
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
    if (!yamlMap.containsKey("extensions")) {
      return ImmutableSet.of();
    }

    List<Map<String, Object>> extensionList = (List<Map<String, Object>>) yamlMap.get("extensions");
    ImmutableSet.Builder<ExtensionConfig> extensionConfigBuilder = ImmutableSet.builder();
    for (Map<String,Object> extensionMap : extensionList) {
      String name = getStringOrDefault(extensionMap, "name");
      int version = getIntegerOrDefault(extensionMap, "version");

      extensionConfigBuilder.add(ExtensionConfig.of(name, version));
    }
    return extensionConfigBuilder.build();
  }

  private static ImmutableSet<FunctionDecl> parseFunctions(Map<String, Object> yamlMap) {
    if (!yamlMap.containsKey("functions")) {
      return ImmutableSet.of();
    }

    List<Map<String, Object>> functionList = (List<Map<String, Object>>) yamlMap.get("functions");
    ImmutableSet.Builder<FunctionDecl> functionDeclBuilder = ImmutableSet.builder();
    for (Map<String,Object> functionMap : functionList) {
      List<Map<String, Object>> overloadList = (List<Map<String, Object>>) functionMap.get("overloads");
      ImmutableSet.Builder<OverloadDecl> overloadDeclBuilder = ImmutableSet.builder();
      for (Map<String,Object> overloadMap : overloadList) {
        OverloadDecl.Builder builder = OverloadDecl.newBuilder()
                .setOverloadId(getStringOrDefault(overloadMap, "id"))
                ;
        if (overloadMap.containsKey("target")) {
          Map<String, Object> typeMap = (Map<String, Object>) overloadMap.get("target");
          builder.setTarget(parseTypeDecl(typeMap));
        }
        if (overloadMap.containsKey("args")) {
          List<Map<String, Object>> argumentList = (List<Map<String, Object>>) overloadMap.get("args");
          for (Map<String, Object> argumentMap : argumentList) {
            builder.addArguments(parseTypeDecl(argumentMap));
          }
        }
        if (overloadMap.containsKey("return")) {
          Map<String, Object> returnMap = (Map<String, Object>) overloadMap.get("return");
          builder.setReturnType(parseTypeDecl(returnMap));
        }

        overloadDeclBuilder.add(builder.build());
      }

      functionDeclBuilder.add(FunctionDecl.create(getStringOrDefault(functionMap, "name"), overloadDeclBuilder.build()));
    }

    return functionDeclBuilder.build();
  }

  private static TypeDecl parseTypeDecl(Map<String, Object> typeMap) {
    TypeDecl.Builder builder = TypeDecl.newBuilder()
            .setName(getStringOrDefault(typeMap, "type_name"))
            .setIsTypeParam(getBooleanOrDefault(typeMap, "is_type_param"));
    if (typeMap.containsKey("params")) {
      List<Map<String, Object>> paramsList = (List<Map<String, Object>>) typeMap.get("params");
      for (Map<String, Object> paramMap : paramsList) {
        builder.addParams(parseTypeDecl(paramMap));
      }
    }

    return builder.build();
  }

  private static boolean getBooleanOrDefault(Map<String, Object> map, String key) {
    return (boolean) map.computeIfAbsent(key, (unused) -> false);
  }

  private static String getStringOrDefault(Map<String, Object> map, String key) {
    return (String) map.computeIfAbsent(key, (unused) -> "");
  }

  private static int getIntegerOrDefault(Map<String, Object> map, String key) {
    return (int) map.computeIfAbsent(key, (unused) -> 0);
  }

  private static Map<String, Object> parseYamlSource(String content) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    return yaml.load(content);
  }

  private YamlPolicyConfigParser() {
  }
}
