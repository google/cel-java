// Copyright 2024 Google LLC
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

package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;
import static dev.cel.policy.YamlHelper.assertRequiredFields;
import static dev.cel.policy.YamlHelper.assertYamlType;
import static dev.cel.policy.YamlHelper.newValueString;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.internal.CelCodePointArray;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Variable;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.util.Optional;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

final class CelPolicyYamlParser implements CelPolicyParser {

  private final TagVisitor<Node> tagVisitor;

  @Override
  public CelPolicy parse(String policySource) throws CelPolicyValidationException {
    return parse(policySource, "<input>");
  }

  @Override
  public CelPolicy parse(String policySource, String description)
      throws CelPolicyValidationException {
    ParserImpl parser = new ParserImpl(tagVisitor);
    return parser.parseYaml(policySource, description);
  }

  private static class ParserImpl {

    private final TagVisitor<Node> tagVisitor;

    private CelPolicy parseYaml(String policySource, String description)
        throws CelPolicyValidationException {
      Node node;
      try {
        node = YamlHelper.parseYamlSource(policySource);
      } catch (RuntimeException e) {
        throw new CelPolicyValidationException("YAML document is malformed: " + e.getMessage(), e);
      }

      CelCodePointArray policyCodePoints = CelCodePointArray.fromString(policySource);
      ParserContext<Node> ctx = YamlParserContextImpl.newInstance(policyCodePoints);

      CelPolicy.Builder policyBuilder = parsePolicy(ctx, node);
      CelPolicySource celPolicySource =
          CelPolicySource.newBuilder(policyCodePoints)
              .setDescription(description)
              .setPositionsMap(ctx.getIdToOffsetMap())
              .build();

      if (ctx.hasError()) {
        throw new CelPolicyValidationException(ctx.getIssueString(celPolicySource));
      }

      return policyBuilder.setPolicySource(celPolicySource).build();
    }

    private CelPolicy.Builder parsePolicy(ParserContext<Node> ctx, Node node) {
      CelPolicy.Builder policyBuilder = CelPolicy.newBuilder();
      long id = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
        return policyBuilder;
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
            policyBuilder.setName(newValueString(ctx, valueNode));
            break;
          case "rule":
            policyBuilder.setRule(parseRule(ctx, valueNode));
            break;
          default:
            tagVisitor.visitPolicyTag(ctx, keyId, fieldName, valueNode, policyBuilder);
            break;
        }
      }

      return policyBuilder;
    }

    private CelPolicy.Rule parseRule(ParserContext<Node> ctx, Node node) {
      long valueId = ctx.collectMetadata(node);
      CelPolicy.Rule.Builder ruleBuilder = CelPolicy.Rule.newBuilder();
      if (!assertYamlType(ctx, valueId, node, YamlNodeType.MAP)) {
        return ruleBuilder.build();
      }

      for (NodeTuple nodeTuple : ((MappingNode) node).getValue()) {
        Node key = nodeTuple.getKeyNode();
        long tagId = ctx.collectMetadata(key);
        if (!assertYamlType(ctx, tagId, key, YamlNodeType.STRING, YamlNodeType.TEXT)) {
          continue;
        }
        String fieldName = ((ScalarNode) key).getValue();
        Node value = nodeTuple.getValueNode();
        switch (fieldName) {
          case "id":
            ruleBuilder.setId(newValueString(ctx, value));
            break;
          case "description":
            ruleBuilder.setDescription(newValueString(ctx, value));
            break;
          case "variables":
            ruleBuilder.addVariables(parseVariables(ctx, value));
            break;
          case "match":
            ruleBuilder.addMatches(parseMatches(ctx, value));
            break;
          default:
            tagVisitor.visitRuleTag(ctx, tagId, fieldName, value, ruleBuilder);
            break;
        }
      }
      return ruleBuilder.build();
    }

    private ImmutableSet<CelPolicy.Match> parseMatches(ParserContext<Node> ctx, Node node) {
      long valueId = ctx.collectMetadata(node);
      ImmutableSet.Builder<CelPolicy.Match> matchesBuilder = ImmutableSet.builder();
      if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
        return matchesBuilder.build();
      }

      SequenceNode matchListNode = (SequenceNode) node;
      for (Node elementNode : matchListNode.getValue()) {
        parseMatch(ctx, elementNode).ifPresent(matchesBuilder::add);
      }

      return matchesBuilder.build();
    }

    private Optional<CelPolicy.Match> parseMatch(ParserContext<Node> ctx, Node node) {
      long nodeId = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, nodeId, node, YamlNodeType.MAP)) {
        return Optional.empty();
      }
      MappingNode matchNode = (MappingNode) node;
      CelPolicy.Match.Builder matchBuilder =
          CelPolicy.Match.newBuilder().setCondition(ValueString.of(ctx.nextId(), "true"));
      for (NodeTuple nodeTuple : matchNode.getValue()) {
        Node key = nodeTuple.getKeyNode();
        long tagId = ctx.collectMetadata(key);
        if (!assertYamlType(ctx, tagId, key, YamlNodeType.STRING, YamlNodeType.TEXT)) {
          continue;
        }
        String fieldName = ((ScalarNode) key).getValue();
        Node value = nodeTuple.getValueNode();
        switch (fieldName) {
          case "condition":
            matchBuilder.setCondition(newValueString(ctx, value));
            break;
          case "output":
            matchBuilder
                .result()
                .filter(result -> result.kind().equals(Match.Result.Kind.RULE))
                .ifPresent(
                    result -> ctx.reportError(tagId, "Only the rule or the output may be set"));
            matchBuilder.setResult(Match.Result.ofOutput(newValueString(ctx, value)));
            break;
          case "rule":
            matchBuilder
                .result()
                .filter(result -> result.kind().equals(Match.Result.Kind.OUTPUT))
                .ifPresent(
                    result -> ctx.reportError(tagId, "Only the rule or the output may be set"));
            matchBuilder.setResult(Match.Result.ofRule(parseRule(ctx, value)));
            break;
          default:
            tagVisitor.visitMatchTag(ctx, tagId, fieldName, value, matchBuilder);
            break;
        }
      }

      if (!assertRequiredFields(ctx, nodeId, matchBuilder.getMissingRequiredFieldNames())) {
        return Optional.empty();
      }

      return Optional.of(matchBuilder.build());
    }

    private ImmutableSet<CelPolicy.Variable> parseVariables(ParserContext<Node> ctx, Node node) {
      long valueId = ctx.collectMetadata(node);
      ImmutableSet.Builder<CelPolicy.Variable> variableBuilder = ImmutableSet.builder();
      if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
        return variableBuilder.build();
      }

      SequenceNode variableListNode = (SequenceNode) node;
      for (Node elementNode : variableListNode.getValue()) {
        long id = ctx.collectMetadata(elementNode);
        if (!assertYamlType(ctx, id, elementNode, YamlNodeType.MAP)) {
          continue;
        }
        variableBuilder.add(parseVariable(ctx, (MappingNode) elementNode));
      }

      return variableBuilder.build();
    }

    private CelPolicy.Variable parseVariable(ParserContext<Node> ctx, MappingNode variableMap) {
      Variable.Builder builder = Variable.newBuilder();

      for (NodeTuple nodeTuple : variableMap.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        long keyId = ctx.collectMetadata(keyNode);
        Node valueNode = nodeTuple.getValueNode();
        String keyName = ((ScalarNode) keyNode).getValue();
        switch (keyName) {
          case "name":
            builder.setName(newValueString(ctx, valueNode));
            break;
          case "expression":
            builder.setExpression(newValueString(ctx, valueNode));
            break;
          default:
            tagVisitor.visitVariableTag(ctx, keyId, keyName, valueNode, builder);
            break;
        }
      }

      return builder.build();
    }

    private ParserImpl(TagVisitor<Node> tagVisitor) {
      this.tagVisitor = tagVisitor;
    }
  }

  static final class Builder implements CelPolicyParserBuilder<Node> {

    private TagVisitor<Node> tagVisitor;

    private Builder() {
      this.tagVisitor = new TagVisitor<Node>() {};
    }

    @Override
    public CelPolicyParserBuilder<Node> addTagVisitor(TagVisitor<Node> tagVisitor) {
      this.tagVisitor = tagVisitor;
      return this;
    }

    @Override
    public CelPolicyParser build() {
      return new CelPolicyYamlParser(tagVisitor);
    }
  }

  static Builder newBuilder() {
    return new Builder();
  }

  private CelPolicyYamlParser(TagVisitor<Node> tagVisitor) {
    this.tagVisitor = checkNotNull(tagVisitor);
  }
}
