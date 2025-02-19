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

import static dev.cel.common.formats.YamlHelper.ERROR;
import static dev.cel.common.formats.YamlHelper.assertRequiredFields;
import static dev.cel.common.formats.YamlHelper.assertYamlType;
import static dev.cel.common.formats.YamlHelper.newBoolean;
import static dev.cel.common.formats.YamlHelper.newInteger;
import static dev.cel.common.formats.YamlHelper.newString;
import static dev.cel.common.formats.YamlHelper.parseYamlSource;
import static dev.cel.common.formats.YamlHelper.validateYamlType;
import static java.util.Collections.singletonList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironment.FunctionDecl;
import dev.cel.bundle.CelEnvironment.OverloadDecl;
import dev.cel.bundle.CelEnvironment.TypeDecl;
import dev.cel.bundle.CelEnvironment.VariableDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.formats.CelFileSource;
import dev.cel.common.formats.ParserContext;
import dev.cel.common.formats.YamlHelper.YamlNodeType;
import dev.cel.common.formats.YamlParserContextImpl;
import dev.cel.common.internal.CelCodePointArray;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

final class CelEnvironmentYamlParser {
  // Sentinel values to be returned for various declarations when parsing failure is encountered.
  private static final TypeDecl ERROR_TYPE_DECL = TypeDecl.create(ERROR);
  private static final VariableDecl ERROR_VARIABLE_DECL =
      VariableDecl.create(ERROR, ERROR_TYPE_DECL);
  private static final FunctionDecl ERROR_FUNCTION_DECL =
      FunctionDecl.create(ERROR, ImmutableSet.of());
  private static final ExtensionConfig ERROR_EXTENSION_DECL = ExtensionConfig.of(ERROR);

  /** Generates a new instance of {@code CelEnvironmentYamlParser}. */
  public static CelEnvironmentYamlParser newInstance() {
    return new CelEnvironmentYamlParser();
  }

  /** Parsers the input {@code environmentYamlSource} and returns a {@link CelEnvironment}. */
  public CelEnvironment parse(String environmentYamlSource) throws CelEnvironmentException {
    return parse(environmentYamlSource, "<input>");
  }

  /**
   * Parses the input {@code environmentYamlSource} and returns a {@link CelEnvironment}.
   *
   * <p>The {@code description} may be used to help tailor error messages for the location where the
   * {@code environmentYamlSource} originates, e.g. a file name or form UI element.
   */
  public CelEnvironment parse(String environmentYamlSource, String description)
      throws CelEnvironmentException {
    CelEnvironmentYamlParser.ParserImpl parser = new CelEnvironmentYamlParser.ParserImpl();

    return parser.parseYaml(environmentYamlSource, description);
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
    TypeDecl.Builder typeDeclBuilder = null;
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
          if (typeDeclBuilder != null) {
            ctx.reportError(
                keyId,
                String.format(
                    "'type' tag cannot be used together with inlined 'type_name', 'is_type_param'"
                        + " or 'params': %s",
                    keyName));
            break;
          }
          builder.setType(parseTypeDecl(ctx, valueNode));
          break;
        case "type_name":
        case "is_type_param":
        case "params":
          if (typeDeclBuilder == null) {
            typeDeclBuilder = TypeDecl.newBuilder();
          }
          typeDeclBuilder = parseInlinedTypeDecl(ctx, keyId, keyNode, valueNode, typeDeclBuilder);
          break;
        default:
          ctx.reportError(keyId, String.format("Unsupported variable tag: %s", keyName));
          break;
      }
    }

    if (typeDeclBuilder != null) {
      if (!assertRequiredFields(ctx, variableId, typeDeclBuilder.getMissingRequiredFieldNames())) {
        return ERROR_VARIABLE_DECL;
      }
      builder.setType(typeDeclBuilder.build());
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

      if (assertRequiredFields(
          ctx, overloadMapId, overloadDeclBuilder.getMissingRequiredFieldNames())) {
        overloadSetBuilder.add(overloadDeclBuilder.build());
      }
    }

    return overloadSetBuilder.build();
  }

  private static ImmutableList<TypeDecl> parseOverloadArguments(
      ParserContext<Node> ctx, Node node) {
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
          if (validateYamlType(valueNode, YamlNodeType.INTEGER)) {
            builder.setVersion(newInteger(ctx, valueNode));
            break;
          } else if (validateYamlType(valueNode, YamlNodeType.STRING, YamlNodeType.TEXT)) {
            String versionStr = newString(ctx, valueNode);
            if (versionStr.equals("latest")) {
              builder.setVersion(Integer.MAX_VALUE);
              break;
            }

            Integer versionInt = tryParse(versionStr);
            if (versionInt != null) {
              builder.setVersion(versionInt);
              break;
            }
            // Fall-through
          }
          ctx.reportError(keyId, String.format("Unsupported version tag: %s", keyName));
          break;
        default:
          ctx.reportError(keyId, String.format("Unsupported extension tag: %s", keyName));
          break;
      }
    }

    if (!assertRequiredFields(ctx, extensionId, builder.getMissingRequiredFieldNames())) {
      return ERROR_EXTENSION_DECL;
    }

    return builder.build();
  }

  private static @Nullable Integer tryParse(String str) {
    try {
      return Integer.parseInt(str);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @CanIgnoreReturnValue
  private static TypeDecl.Builder parseInlinedTypeDecl(
      ParserContext<Node> ctx, long keyId, Node keyNode, Node valueNode, TypeDecl.Builder builder) {
    if (!assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING, YamlNodeType.TEXT)) {
      return builder;
    }

    // Create a synthetic node to make this behave as if a `type: ` parent node actually exists.
    MappingNode mapNode =
        new MappingNode(
            Tag.MAP, /* value= */ singletonList(new NodeTuple(keyNode, valueNode)), FlowStyle.AUTO);

    return parseTypeDeclFields(ctx, mapNode, builder);
  }

  private static TypeDecl parseTypeDecl(ParserContext<Node> ctx, Node node) {
    TypeDecl.Builder builder = TypeDecl.newBuilder();
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
      return ERROR_TYPE_DECL;
    }

    MappingNode mapNode = (MappingNode) node;
    return parseTypeDeclFields(ctx, mapNode, builder).build();
  }

  @CanIgnoreReturnValue
  private static TypeDecl.Builder parseTypeDeclFields(
      ParserContext<Node> ctx, MappingNode mapNode, TypeDecl.Builder builder) {
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
          long listValueId = ctx.collectMetadata(valueNode);
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
    return builder;
  }

  private class ParserImpl {

    private CelEnvironment parseYaml(String source, String description)
        throws CelEnvironmentException {
      Node node;
      try {
        node =
            parseYamlSource(source)
                .orElseThrow(
                    () ->
                        new CelEnvironmentException(
                            String.format("YAML document empty or malformed: %s", source)));
      } catch (RuntimeException e) {
        throw new CelEnvironmentException("YAML document is malformed: " + e.getMessage(), e);
      }

      CelFileSource environmentSource =
          CelFileSource.newBuilder(CelCodePointArray.fromString(source))
              .setDescription(description)
              .build();
      ParserContext<Node> ctx = YamlParserContextImpl.newInstance(environmentSource);
      CelEnvironment.Builder builder = parseConfig(ctx, node);
      environmentSource =
          environmentSource.toBuilder().setPositionsMap(ctx.getIdToOffsetMap()).build();

      if (!ctx.getIssues().isEmpty()) {
        throw new CelEnvironmentException(
            CelIssue.toDisplayString(ctx.getIssues(), environmentSource));
      }

      return builder.setSource(environmentSource).build();
    }

    private CelEnvironment.Builder parseConfig(ParserContext<Node> ctx, Node node) {
      CelEnvironment.Builder builder = CelEnvironment.newBuilder();
      long id = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
        return builder;
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
          default:
            ctx.reportError(id, "Unknown config tag: " + fieldName);
            // continue handling the rest of the nodes
        }
      }

      return builder;
    }
  }

  private CelEnvironmentYamlParser() {}
}
