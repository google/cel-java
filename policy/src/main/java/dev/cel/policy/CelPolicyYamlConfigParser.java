package dev.cel.policy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.policy.CelPolicyConfig.ExtensionConfig;
import dev.cel.policy.CelPolicyConfig.FunctionDecl;
import dev.cel.policy.CelPolicyConfig.OverloadDecl;
import dev.cel.policy.CelPolicyConfig.TypeDecl;
import dev.cel.policy.CelPolicyConfig.VariableDecl;
import dev.cel.policy.YamlHelper.YamlNodeType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import static dev.cel.policy.YamlHelper.ERROR;
import static dev.cel.policy.YamlHelper.assertRequiredFields;
import static dev.cel.policy.YamlHelper.assertYamlType;
import static dev.cel.policy.YamlHelper.newString;
import static dev.cel.policy.YamlHelper.newBoolean;
import static dev.cel.policy.YamlHelper.newInteger;
import static dev.cel.policy.YamlHelper.parseYamlSource;

final class CelPolicyYamlConfigParser implements CelPolicyConfigParser {
  // Sentinel values to be returned for various declarations when parsing failure is encountered.
  private static final TypeDecl ERROR_TYPE_DECL = TypeDecl.create(ERROR);
  private static final VariableDecl ERROR_VARIABLE_DECL = VariableDecl.create(ERROR, ERROR_TYPE_DECL);
  private static final FunctionDecl ERROR_FUNCTION_DECL = FunctionDecl.create(ERROR, ImmutableSet.of());
  private static final ExtensionConfig ERROR_EXTENSION_DECL = ExtensionConfig.of(ERROR);

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
            builder.setFunctions(parseFunctions(ctx, valueNode));
            break;
          case "extensions":
            builder.setExtensions(parseExtensions(ctx, valueNode));
            break;
        }
      }

      return builder.build();
    }
  }

  @Override
  public CelPolicyConfig parse(CelPolicySource policyConfigSource)
      throws CelPolicyValidationException {
    ParserImpl parser = new ParserImpl();

    return parser.parseYaml(policyConfigSource);
  }

  private ImmutableSet<VariableDecl> parseVariables(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    ImmutableSet.Builder<VariableDecl> variableSetBuilder = ImmutableSet.builder();
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
      return variableSetBuilder.build();
    }

    SequenceNode variableListNode = (SequenceNode) node;
    for (Node elementNode : variableListNode.getValue()) {
      variableSetBuilder.add(parseVariable(ctx, elementNode));
    }

    return variableSetBuilder.build();
  }


  private VariableDecl parseVariable(ParserContext<Node> ctx, Node node) {
    long variableId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, variableId, node, YamlNodeType.MAP)) {
      return ERROR_VARIABLE_DECL;
    }

    MappingNode variableMap = (MappingNode) node;
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

    if (!assertRequiredFields(ctx, variableId, builder.getMissingRequiredFieldNames())) {
      return ERROR_VARIABLE_DECL;
    }

    return builder.build();
  }

  private ImmutableSet<FunctionDecl> parseFunctions(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    ImmutableSet.Builder<FunctionDecl> functionSetBuilder = ImmutableSet.builder();
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
      return functionSetBuilder.build();
    }

    SequenceNode functionListNode = (SequenceNode) node;
    for (Node elementNode : functionListNode.getValue()) {
      functionSetBuilder.add(parseFunction(ctx, elementNode));
    }

    return functionSetBuilder.build();
  }

  private FunctionDecl parseFunction(ParserContext<Node> ctx, Node node) {
    long functionId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, functionId, node, YamlNodeType.MAP)) {
      return ERROR_FUNCTION_DECL;
    }

    MappingNode functionMap = (MappingNode) node;
    FunctionDecl.Builder builder = FunctionDecl.newBuilder();
    for (NodeTuple nodeTuple : functionMap.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      Node valueNode = nodeTuple.getValueNode();
      String keyName = ((ScalarNode) keyNode).getValue();
      switch (keyName) {
        case "name":
          builder.setName(newString(ctx, valueNode));
          break;
        case "overloads":
          builder.setOverloads(parseOverloads(ctx, valueNode));
          break;
        default:
          ctx.reportError(keyId, String.format("Unsupported function tag: %s", keyName));
          break;
      }
    }

    if (!assertRequiredFields(ctx, functionId, builder.getMissingRequiredFieldNames())) {
      return ERROR_FUNCTION_DECL;
    }

    return builder.build();
  }

  private static ImmutableSet<OverloadDecl> parseOverloads(ParserContext<Node> ctx, Node node) {
    long listId = ctx.collectMetadata(node);
    ImmutableSet.Builder<OverloadDecl> overloadSetBuilder = ImmutableSet.builder();
    if (!assertYamlType(ctx, listId, node, YamlNodeType.LIST)) {
      return overloadSetBuilder.build();
    }

    SequenceNode overloadListNode = (SequenceNode) node;
    for (Node overloadMapNode : overloadListNode.getValue()) {
      long overloadMapId = ctx.collectMetadata(overloadMapNode);
      if (!assertYamlType(ctx, overloadMapId, overloadMapNode, YamlNodeType.MAP)) {
        continue;
      }

      MappingNode mapNode = (MappingNode) overloadMapNode;
      OverloadDecl.Builder overloadDeclBuilder = OverloadDecl.newBuilder();
      for (NodeTuple nodeTuple : mapNode.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        long keyId = ctx.collectMetadata(keyNode);
        if (!assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING, YamlNodeType.TEXT)) {
          continue;
        }

        Node valueNode = nodeTuple.getValueNode();
        String fieldName = ((ScalarNode) keyNode).getValue();
        switch (fieldName) {
          case "id":
            overloadDeclBuilder.setId(newString(ctx, valueNode));
            break;
          case "args":
            overloadDeclBuilder.addArguments(parseOverloadArguments(ctx, valueNode));
            break;
          case "return":
            overloadDeclBuilder.setReturnType(parseTypeDecl(ctx, valueNode));
            break;
          case "target":
            overloadDeclBuilder.setTarget(parseTypeDecl(ctx, valueNode));
            break;
          default:
            ctx.reportError(keyId, String.format("Unsupported overload tag: %s", fieldName));
            break;
        }
      }

      if (assertRequiredFields(ctx, overloadMapId, overloadDeclBuilder.getMissingRequiredFieldNames())) {
        overloadSetBuilder.add(overloadDeclBuilder.build());
      }
    }

    return overloadSetBuilder.build();
  }


  private static ImmutableList<TypeDecl> parseOverloadArguments(ParserContext<Node> ctx, Node node) {
    long listValueId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, listValueId, node, YamlNodeType.LIST)) {
      return ImmutableList.of();
    }
    SequenceNode paramsListNode = (SequenceNode) node;
    ImmutableList.Builder<TypeDecl> builder = ImmutableList.builder();
    for (Node elementNode : paramsListNode.getValue()) {
      builder.add(parseTypeDecl(ctx, elementNode));
    }

    return builder.build();
  }

  private static ImmutableSet<ExtensionConfig> parseExtensions(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    ImmutableSet.Builder<ExtensionConfig> extensionConfigBuilder = ImmutableSet.builder();
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
      return extensionConfigBuilder.build();
    }

    SequenceNode extensionListNode = (SequenceNode) node;
    for (Node elementNode : extensionListNode.getValue()) {
      extensionConfigBuilder.add(parseExtension(ctx, elementNode));
    }

    return extensionConfigBuilder.build();
  }

  private static ExtensionConfig parseExtension(ParserContext<Node> ctx, Node node) {
    long extensionId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, extensionId, node, YamlNodeType.MAP)) {
      return ERROR_EXTENSION_DECL;
    }

    MappingNode extensionMap = (MappingNode) node;
    ExtensionConfig.Builder builder = ExtensionConfig.newBuilder();
    for (NodeTuple nodeTuple : extensionMap.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      Node valueNode = nodeTuple.getValueNode();
      String keyName = ((ScalarNode) keyNode).getValue();
      switch (keyName) {
        case "name":
          builder.setName(newString(ctx, valueNode));
          break;
        case "version":
          builder.setVersion(newInteger(ctx, valueNode));
          break;
        default:
          ctx.reportError(keyId, String.format("Unsupported variable tag: %s", keyName));
          break;
      }
    }

    if (!assertRequiredFields(ctx, extensionId, builder.getMissingRequiredFieldNames())) {
      return ERROR_EXTENSION_DECL;
    }

    return builder.build();
  }

  private static TypeDecl parseTypeDecl(ParserContext<Node> ctx, Node node) {
    TypeDecl.Builder builder = TypeDecl.newBuilder();
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
      return ERROR_TYPE_DECL;
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
          builder.setIsTypeParam(newBoolean(ctx, valueNode));
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

    return builder.build();
  }

  static CelPolicyYamlConfigParser newInstance() {
    return new CelPolicyYamlConfigParser();
  }

  private CelPolicyYamlConfigParser() {
  }
}
