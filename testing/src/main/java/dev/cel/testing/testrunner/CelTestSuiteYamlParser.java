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
package dev.cel.testing.testrunner;

import static dev.cel.common.formats.YamlHelper.YamlNodeType.nodeType;
import static dev.cel.common.formats.YamlHelper.assertYamlType;
import static dev.cel.common.formats.YamlHelper.newBoolean;
import static dev.cel.common.formats.YamlHelper.newDouble;
import static dev.cel.common.formats.YamlHelper.newInteger;
import static dev.cel.common.formats.YamlHelper.newString;
import static dev.cel.common.formats.YamlHelper.parseYamlSource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelIssue;
import dev.cel.common.formats.CelFileSource;
import dev.cel.common.formats.ParserContext;
import dev.cel.common.formats.YamlHelper.YamlNodeType;
import dev.cel.common.formats.YamlParserContextImpl;
import dev.cel.common.internal.CelCodePointArray;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Input.Binding;
import java.util.Optional;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * CelTestSuiteYamlParser intakes a YAML document that describes the structure of a CEL test suite,
 * parses it then creates a {@link CelTestSuite}.
 */
final class CelTestSuiteYamlParser {

  /** Creates a new instance of {@link CelTestSuiteYamlParser}. */
  static CelTestSuiteYamlParser newInstance() {
    return new CelTestSuiteYamlParser();
  }

  CelTestSuite parse(String celTestSuiteYamlContent) throws CelTestSuiteException {
    return parseYaml(celTestSuiteYamlContent, "<input>");
  }

  private CelTestSuite parseYaml(String celTestSuiteYamlContent, String description)
      throws CelTestSuiteException {
    Node node;
    try {
      node =
          parseYamlSource(celTestSuiteYamlContent)
              .orElseThrow(
                  () ->
                      new CelTestSuiteException(
                          String.format(
                              "YAML document empty or malformed: %s", celTestSuiteYamlContent)));
    } catch (RuntimeException e) {
      throw new CelTestSuiteException("YAML document is malformed: " + e.getMessage(), e);
    }

    CelFileSource testSuiteSource =
        CelFileSource.newBuilder(CelCodePointArray.fromString(celTestSuiteYamlContent))
            .setDescription(description)
            .build();
    ParserContext<Node> ctx = YamlParserContextImpl.newInstance(testSuiteSource);
    CelTestSuite.Builder builder = parseTestSuite(ctx, node);
    testSuiteSource = testSuiteSource.toBuilder().setPositionsMap(ctx.getIdToOffsetMap()).build();

    if (!ctx.getIssues().isEmpty()) {
      throw new CelTestSuiteException(CelIssue.toDisplayString(ctx.getIssues(), testSuiteSource));
    }

    return builder.setSource(testSuiteSource).build();
  }

  private CelTestSuite.Builder parseTestSuite(ParserContext<Node> ctx, Node node) {
    CelTestSuite.Builder builder = CelTestSuite.newBuilder();
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
      ctx.reportError(id, "Unknown test suite type: " + node.getTag());
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
        case "sections":
          builder.setSections(parseSections(ctx, valueNode));
          break;
        default:
          ctx.reportError(keyId, "Unknown test suite tag: " + fieldName);
          break;
      }
    }
    return builder;
  }

  private ImmutableSet<CelTestSection> parseSections(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    ImmutableSet.Builder<CelTestSection> celTestSectionSetBuilder = ImmutableSet.builder();
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
      ctx.reportError(valueId, "Sections is not a list: " + node.getTag());
      return celTestSectionSetBuilder.build();
    }

    SequenceNode sectionListNode = (SequenceNode) node;
    for (Node elementNode : sectionListNode.getValue()) {
      celTestSectionSetBuilder.add(parseSection(ctx, elementNode));
    }
    return celTestSectionSetBuilder.build();
  }

  private CelTestSection parseSection(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.MAP)) {
      ctx.reportError(valueId, "Unknown section type: " + node.getTag());
      return CelTestSection.newBuilder().build();
    }

    CelTestSection.Builder celTestSectionBuilder = CelTestSection.newBuilder();
    MappingNode sectionNode = (MappingNode) node;
    for (NodeTuple nodeTuple : sectionNode.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      Node valueNode = nodeTuple.getValueNode();
      String fieldName = ((ScalarNode) keyNode).getValue();
      switch (fieldName) {
        case "name":
          celTestSectionBuilder.setName(newString(ctx, valueNode));
          break;
        case "description":
          celTestSectionBuilder.setDescription(newString(ctx, valueNode));
          break;
        case "tests":
          celTestSectionBuilder.setTests(parseTests(ctx, valueNode));
          break;
        default:
          ctx.reportError(keyId, "Unknown test section tag: " + fieldName);
          break;
      }
    }
    return celTestSectionBuilder.build();
  }

  private ImmutableSet<CelTestCase> parseTests(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    ImmutableSet.Builder<CelTestCase> celTestCaseSetBuilder = ImmutableSet.builder();
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
      ctx.reportError(valueId, "Tests is not a list: " + node.getTag());
      return celTestCaseSetBuilder.build();
    }

    SequenceNode testCasesListNode = (SequenceNode) node;
    for (Node elementNode : testCasesListNode.getValue()) {
      celTestCaseSetBuilder.add(parseTestCase(ctx, elementNode));
    }
    return celTestCaseSetBuilder.build();
  }

  private CelTestCase parseTestCase(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    CelTestCase.Builder celTestCaseBuilder = CelTestCase.newBuilder();
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.MAP)) {
      ctx.reportError(valueId, "Testcase is not a map: " + node.getTag());
      return celTestCaseBuilder.build();
    }
    MappingNode testCaseNode = (MappingNode) node;
    for (NodeTuple nodeTuple : testCaseNode.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      Node valueNode = nodeTuple.getValueNode();
      String fieldName = ((ScalarNode) keyNode).getValue();
      switch (fieldName) {
        case "name":
          celTestCaseBuilder.setName(newString(ctx, valueNode));
          break;
        case "description":
          celTestCaseBuilder.setDescription(newString(ctx, valueNode));
          break;
        case "input":
          celTestCaseBuilder.setInput(parseInput(ctx, valueNode));
          break;
        case "context_expr":
          celTestCaseBuilder.setInput(parseContextExpr(ctx, valueNode));
          break;
        case "output":
          celTestCaseBuilder.setOutput(parseOutput(ctx, valueNode));
          break;
        default:
          ctx.reportError(keyId, "Unknown test case tag: " + fieldName);
          break;
      }
    }
    return celTestCaseBuilder.build();
  }

  private CelTestCase.Input parseInput(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.MAP)) {
      ctx.reportError(valueId, "Input is not a map: " + node.getTag());
      return CelTestCase.Input.ofNoInput();
    }
    MappingNode inputNode = (MappingNode) node;
    ImmutableMap.Builder<String, Binding> bindingsBuilder = ImmutableMap.builder();
    for (NodeTuple nodeTuple : inputNode.getValue()) {
      Node valueNode = nodeTuple.getValueNode();
      Optional<Binding> binding = parseBindingValueNode(ctx, valueNode);
      binding.ifPresent(
          b -> bindingsBuilder.put(((ScalarNode) nodeTuple.getKeyNode()).getValue(), b));
    }
    return CelTestCase.Input.ofBindings(bindingsBuilder.buildOrThrow());
  }

  private Optional<Binding> parseBindingValueNode(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.MAP)) {
      ctx.reportError(valueId, "Input binding node is not a map: " + node.getTag());
      return Optional.empty();
    }
    MappingNode bindingValueNode = (MappingNode) node;

    if (bindingValueNode.getValue().size() != 1) {
      ctx.reportError(valueId, "Input binding node must have exactly one value: " + node.getTag());
      return Optional.empty();
    }

    for (NodeTuple nodeTuple : bindingValueNode.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      Node valueNode = nodeTuple.getValueNode();
      String fieldName = ((ScalarNode) keyNode).getValue();
      switch (fieldName) {
        case "value":
          return Optional.of(Binding.ofValue(parseNodeValue(ctx, valueNode)));
        case "expr":
          return Optional.of(Binding.ofExpr(newString(ctx, valueNode)));
        default:
          ctx.reportError(keyId, "Unknown input binding value tag: " + fieldName);
          break;
      }
    }
    return Optional.empty();
  }

  // TODO: Create a CelTestSuiteNodeValue class to represent the value of a test suite
  // node.
  private Object parseNodeValue(ParserContext<Node> ctx, Node node) {
    Object value = null;
    Optional<YamlNodeType> yamlNodeType = nodeType(node.getTag().getValue());
    if (yamlNodeType.isPresent()) {
      switch (yamlNodeType.get()) {
        case STRING:
        case TEXT:
          value = newString(ctx, node);
          break;
        case BOOLEAN:
          value = newBoolean(ctx, node);
          break;
        case INTEGER:
          value = newInteger(ctx, node);
          break;
        case DOUBLE:
          value = newDouble(ctx, node);
          break;
        case MAP:
          value = parseMap(ctx, node);
          break;
        case LIST:
          value = parseList(ctx, node);
          break;
      }
    }
    return value;
  }

  private ImmutableMap<Object, Object> parseMap(ParserContext<Node> ctx, Node node) {
    ImmutableMap.Builder<Object, Object> mapBuilder = ImmutableMap.builder();
    MappingNode mapNode = (MappingNode) node;
    mapNode
        .getValue()
        .forEach(
            nodeTuple -> {
              Node keyNode = nodeTuple.getKeyNode();
              Node valueNode = nodeTuple.getValueNode();
              mapBuilder.put(parseNodeValue(ctx, keyNode), parseNodeValue(ctx, valueNode));
            });
    return mapBuilder.buildOrThrow();
  }

  private ImmutableList<Object> parseList(ParserContext<Node> ctx, Node node) {
    ImmutableList.Builder<Object> listBuilder = ImmutableList.builder();
    SequenceNode listNode = (SequenceNode) node;
    listNode.getValue().forEach(childNode -> listBuilder.add(parseNodeValue(ctx, childNode)));
    return listBuilder.build();
  }

  private ImmutableList<Long> parseUnknown(ParserContext<Node> ctx, Node node) {
    ImmutableList<Object> unknown = parseList(ctx, node);
    ImmutableList.Builder<Long> unknownBuilder = ImmutableList.builder();
    for (Object object : unknown) {
      if (object instanceof Integer) {
        unknownBuilder.add(Long.valueOf((Integer) object));
      } else {
        ctx.reportError(
            ctx.collectMetadata(node),
            "Only integer ids are supported in unknown list. Found: "
                + object.getClass().getName());
      }
    }
    return unknownBuilder.build();
  }

  private CelTestCase.Input parseContextExpr(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.STRING)) {
      ctx.reportError(valueId, "Input context is not a string: " + node.getTag());
      return CelTestCase.Input.ofNoInput();
    }
    return CelTestCase.Input.ofContextExpr(newString(ctx, node));
  }

  private CelTestCase.Output parseOutput(ParserContext<Node> ctx, Node node) {
    long valueId = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, valueId, node, YamlNodeType.MAP)) {
      ctx.reportError(valueId, "Output is not a map: " + node.getTag());
      return CelTestCase.Output.ofNoOutput();
    }
    MappingNode outputNode = (MappingNode) node;
    for (NodeTuple nodeTuple : outputNode.getValue()) {
      Node keyNode = nodeTuple.getKeyNode();
      long keyId = ctx.collectMetadata(keyNode);
      Node valueNode = nodeTuple.getValueNode();
      String fieldName = ((ScalarNode) keyNode).getValue();
      switch (fieldName) {
        case "value":
          return CelTestCase.Output.ofResultValue(parseNodeValue(ctx, valueNode));
        case "expr":
          return CelTestCase.Output.ofResultExpr(newString(ctx, valueNode));
        case "error_set":
          return CelTestCase.Output.ofEvalError(parseList(ctx, valueNode));
        case "unknown":
          return CelTestCase.Output.ofUnknownSet(parseUnknown(ctx, valueNode));
        default:
          ctx.reportError(keyId, "Unknown output tag: " + fieldName);
          break;
      }
    }
    return CelTestCase.Output.ofNoOutput();
  }

  private CelTestSuiteYamlParser() {}
}
