package dev.cel.policy;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static dev.cel.policy.YamlHelper.assertYamlType;
import static dev.cel.policy.YamlHelper.getListOfMapsOrDefault;
import static dev.cel.policy.YamlHelper.getListOfMapsOrThrow;
import static dev.cel.policy.YamlHelper.getMapOrThrow;
import static dev.cel.policy.YamlHelper.getOrThrow;
import static dev.cel.policy.YamlHelper.parseYamlSource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.policy.CelPolicyConfig.ExtensionConfig;
import dev.cel.policy.CelPolicyConfig.FunctionDecl;
import dev.cel.policy.CelPolicyConfig.OverloadDecl;
import dev.cel.policy.CelPolicyConfig.TypeDecl;
import dev.cel.policy.CelPolicyConfig.VariableDecl;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

final class CelPolicyYamlConfigParser implements CelPolicyConfigParser {
  private static final TypeDecl ERROR_DECL = TypeDecl.create("*error*");

  private class ParserImpl {

    private CelPolicyConfig parseYaml(CelPolicySource source) throws CelPolicyValidationException {
      Node node;
      try {
        node = parseYamlSource(source);
      } catch (Exception e) {
        throw new CelPolicyValidationException(
            "YAML document is malformed: " + e.getMessage(), e);
      }

      ParserContext<Node> ctx = YamlParserContextImpl.newInstance(source);
      CelPolicyConfig policyConfig = parseConfig(ctx, node);

      if (ctx.hasError()) {
        throw new CelPolicyValidationException(ctx.getIssueString());
      }

      return policyConfig;
    }

    private CelPolicyConfig parseConfig(ParserContext<Node> ctx, Node node) {
      CelPolicyConfig.Builder builder = CelPolicyConfig.newBuilder();
      long id = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
        return builder.build();
      }

      MappingNode rootNode = (MappingNode) node;
      for (NodeTuple nodeTuple : rootNode.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        long keyId = ctx.collectMetadata(keyNode);
        if (!assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING, YamlNodeType.TEXT)) {
          continue;
        }

        Node valueNode = nodeTuple.getValueNode();
        String fieldName = ((ScalarNode) keyNode).getValue();
        switch (fieldName) {
          case "name":
            builder.setName(newString(ctx, valueNode));
            break;
          case "description":
            builder.setDescription(newString(ctx, valueNode));
            break;
          case "container":
            builder.setContainer(newString(ctx, valueNode));
            break;
          case "variables":
            builder.setVariables(parseVariables(ctx, valueNode));
            break;
          case "functions":
            break;
          case "extensions":
            break;
        }
      }

      // String name = (String) yamlMap.getOrDefault("name", "");
      // String description = (String) yamlMap.getOrDefault("description", "");
      // String container = (String) yamlMap.getOrDefault("container", "");
      // ImmutableSet<VariableDecl> variables = parseVariables(yamlMap);
      // ImmutableSet<FunctionDecl> functions = parseFunctions(yamlMap);
      // ImmutableSet<ExtensionConfig> extensions = parseExtensions(yamlMap);
      //
      // return CelPolicyConfig.newBuilder()
      //     .setName(name)
      //     .setDescription(description)
      //     .setContainer(container)
      //     .setVariables(variables)
      //     .setFunctions(functions)
      //     .setExtensions(extensions)
      //     .build();

      return builder.build();
    }
  }

  @Override
  public CelPolicyConfig parse(CelPolicySource policyConfigSource)
      throws CelPolicyValidationException {
    ParserImpl parser = new ParserImpl();
    return parser.parseYaml(policyConfigSource);
    // try {
    //   Map<String, Object> yamlMap = parseYamlSource(content);
    //
    //   String name = (String) yamlMap.getOrDefault("name", "");
    //   String description = (String) yamlMap.getOrDefault("description", "");
    //   String container = (String) yamlMap.getOrDefault("container", "");
    //   ImmutableSet<VariableDecl> variables = parseVariables(yamlMap);
    //   ImmutableSet<FunctionDecl> functions = parseFunctions(yamlMap);
    //   ImmutableSet<ExtensionConfig> extensions = parseExtensions(yamlMap);
    //
    //   return CelPolicyConfig.newBuilder()
    //       .setName(name)
    //       .setDescription(description)
    //       .setContainer(container)
    //       .setVariables(variables)
    //       .setFunctions(functions)
    //       .setExtensions(extensions)
    //       .build();
    // } catch (Exception e) {
    //   throw new CelPolicyValidationException(e.getMessage(), e);
    // }
  }

  private ImmutableSet<VariableDecl> parseVariables(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    ImmutableSet.Builder<VariableDecl> variableSetBuilder = ImmutableSet.builder();
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
      return variableSetBuilder.build();
    }

    SequenceNode variableListNode = (SequenceNode) node;
    for (Node elementNode : variableListNode.getValue()) {
      long id = ctx.collectMetadata(elementNode);
      if (!assertYamlType(ctx, id, elementNode, YamlNodeType.MAP)) {
        continue;
      }

      MappingNode variableMap = (MappingNode) elementNode;

      variableSetBuilder.add(parseVariable(ctx, variableMap));
      // newString(ctx, )
      // getOrThrow(variableMap, "name", String.class),
      // parseTypeDecl(getMapOrThrow(variableMap, "type"))

      // variableBuilder.add(parseVariable(ctx, (MappingNode) elementNode));
    }
    // List<Map<String, Object>> variableList = getListOfMapsOrDefault(yamlMap, "variables");
    // for (Map<String, Object> variableMap : variableList) {
    //   variableSetBuilder.add(VariableDecl.create(
    //       getOrThrow(variableMap, "name", String.class),
    //       parseTypeDecl(getMapOrThrow(variableMap, "type"))
    //   ));
    // }

    return variableSetBuilder.build();
  }


  private VariableDecl parseVariable(ParserContext<Node> ctx, MappingNode variableMap) {
    VariableDecl.Builder builder = VariableDecl.newBuilder();
    for (NodeTuple nodeTuple : variableMap.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      Node valueNode = nodeTuple.getValueNode();
      String keyName = ((ScalarNode) keyNode).getValue();
      switch (keyName) {
        case "name":
          builder.setName(newString(ctx, valueNode));
          break;
        case "type":
          builder.setType(parseTypeDecl(ctx, valueNode));
          break;
        default:
          ctx.reportError(keyId, String.format("Unsupported variable tag: %s", keyName));
          break;
      }
    }

    return builder.build();
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

  private static TypeDecl parseTypeDecl(ParserContext<Node> ctx, Node node) {
    TypeDecl.Builder builder = TypeDecl.newBuilder();
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
      return ERROR_DECL;
    }

    MappingNode mapNode = (MappingNode) node;
    for (NodeTuple nodeTuple : mapNode.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      if (!assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING, YamlNodeType.TEXT)) {
        continue;
      }

      Node valueNode = nodeTuple.getValueNode();
      String fieldName = ((ScalarNode) keyNode).getValue();
      switch (fieldName) {
        case "type_name":
          builder.setName(newString(ctx, valueNode));
          break;
        case "is_type_param":
          System.out.println();
          break;
        case "params":
          long listValueId = ctx.collectMetadata(node);
          if (!assertYamlType(ctx, listValueId, valueNode, YamlNodeType.LIST)) {
            break;
          }
          SequenceNode paramsListNode = (SequenceNode) valueNode;
          for (Node elementNode : paramsListNode.getValue()) {
            builder.addParams(parseTypeDecl(ctx, elementNode));
          }
          break;
        default:
          ctx.reportError(keyId, String.format("Unsupported type decl tag: %s", fieldName));
          break;
      }
    }
    //
    // TypeDecl.Builder builder = TypeDecl.newBuilder()
    //     .setName((String) typeMap.getOrDefault("type_name", ""))
    //     .setIsTypeParam((boolean) typeMap.getOrDefault("is_type_param", false));
    //
    // List<Map<String, Object>> paramsList = getListOfMapsOrDefault(typeMap, "params");
    // for (Map<String, Object> paramMap : paramsList) {
    //   builder.addParams(parseTypeDecl(paramMap));
    // }
    //
    return builder.build();
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

  private static String newString(ParserContext<Node> ctx, Node node) {
    return YamlHelper.newString(ctx, node).value();
  }

  static CelPolicyYamlConfigParser newInstance() {
    return new CelPolicyYamlConfigParser();
  }

  private CelPolicyYamlConfigParser() {
  }
}
