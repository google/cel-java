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
import static dev.cel.policy.YamlHelper.ERROR;
import static dev.cel.policy.YamlHelper.assertRequiredFields;
import static dev.cel.policy.YamlHelper.assertYamlType;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelIssue;
import dev.cel.common.internal.CelCodePointArray;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Match.Result;
import dev.cel.policy.CelPolicy.Variable;
import dev.cel.policy.ParserContext.PolicyParserContext;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

final class CelPolicyYamlParser implements CelPolicyParser {

  // Sentinel values for parsing errors
  private static final ValueString ERROR_VALUE = ValueString.newBuilder().setValue(ERROR).build();
  private static final Match ERROR_MATCH =
      Match.newBuilder(0).setCondition(ERROR_VALUE).setResult(Result.ofOutput(ERROR_VALUE)).build();
  private static final Variable ERROR_VARIABLE =
      Variable.newBuilder().setExpression(ERROR_VALUE).setName(ERROR_VALUE).build();

  private final TagVisitor<Node> tagVisitor;

  @Override
  public CelPolicy parse(String policySource) throws CelPolicyValidationException {
    return parse(policySource, "<input>");
  }

  @Override
  public CelPolicy parse(String policySource, String description)
      throws CelPolicyValidationException {
    ParserImpl parser = new ParserImpl(tagVisitor, policySource, description);
    return parser.parseYaml();
  }

  private static class ParserImpl implements PolicyParserContext<Node> {

    private final TagVisitor<Node> tagVisitor;
    private final CelPolicySource policySource;
    private final ParserContext<Node> ctx;

    private CelPolicy parseYaml() throws CelPolicyValidationException {
      Node node;
      try {
        node = YamlHelper.parseYamlSource(policySource.getContent().toString());
      } catch (RuntimeException e) {
        throw new CelPolicyValidationException("YAML document is malformed: " + e.getMessage(), e);
      }

      CelPolicy celPolicy = parsePolicy(this, node);

      if (!ctx.getIssues().isEmpty()) {
        throw new CelPolicyValidationException(
            CelIssue.toDisplayString(ctx.getIssues(), celPolicy.policySource()));
      }

      return celPolicy;
    }

    @Override
    public CelPolicy parsePolicy(PolicyParserContext<Node> ctx, Node node) {
      CelPolicy.Builder policyBuilder = CelPolicy.newBuilder();
      long id = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
        return policyBuilder.setPolicySource(policySource).build();
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
            policyBuilder.setName(ctx.newValueString(valueNode));
            break;
          case "rule":
            policyBuilder.setRule(parseRule(ctx, policyBuilder, valueNode));
            break;
          default:
            tagVisitor.visitPolicyTag(ctx, keyId, fieldName, valueNode, policyBuilder);
            break;
        }
      }

      return policyBuilder
          .setPolicySource(policySource.toBuilder().setPositionsMap(ctx.getIdToOffsetMap()).build())
          .build();
    }

    @Override
    public CelPolicy.Rule parseRule(
        PolicyParserContext<Node> ctx, CelPolicy.Builder policyBuilder, Node node) {
      long valueId = ctx.collectMetadata(node);
      CelPolicy.Rule.Builder ruleBuilder = CelPolicy.Rule.newBuilder(valueId);
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
            ruleBuilder.setRuleId(ctx.newValueString(value));
            break;
          case "description":
            ruleBuilder.setDescription(ctx.newValueString(value));
            break;
          case "variables":
            ruleBuilder.addVariables(parseVariables(ctx, policyBuilder, value));
            break;
          case "match":
            ruleBuilder.addMatches(parseMatches(ctx, policyBuilder, value));
            break;
          default:
            tagVisitor.visitRuleTag(ctx, tagId, fieldName, value, policyBuilder, ruleBuilder);
            break;
        }
      }
      return ruleBuilder.build();
    }

    private ImmutableSet<CelPolicy.Match> parseMatches(
        PolicyParserContext<Node> ctx, CelPolicy.Builder policyBuilder, Node node) {
      long valueId = ctx.collectMetadata(node);
      ImmutableSet.Builder<CelPolicy.Match> matchesBuilder = ImmutableSet.builder();
      if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
        return matchesBuilder.build();
      }

      SequenceNode matchListNode = (SequenceNode) node;
      for (Node elementNode : matchListNode.getValue()) {
        matchesBuilder.add(parseMatch(ctx, policyBuilder, elementNode));
      }

      return matchesBuilder.build();
    }

    @Override
    public CelPolicy.Match parseMatch(
        PolicyParserContext<Node> ctx, CelPolicy.Builder policyBuilder, Node node) {
      long nodeId = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, nodeId, node, YamlNodeType.MAP)) {
        return ERROR_MATCH;
      }
      MappingNode matchNode = (MappingNode) node;
      CelPolicy.Match.Builder matchBuilder =
          CelPolicy.Match.newBuilder(nodeId).setCondition(ValueString.of(ctx.nextId(), "true"));
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
            matchBuilder.setCondition(ctx.newValueString(value));
            break;
          case "output":
            matchBuilder
                .result()
                .filter(result -> result.kind().equals(Match.Result.Kind.RULE))
                .ifPresent(
                    result -> ctx.reportError(tagId, "Only the rule or the output may be set"));
            matchBuilder.setResult(Match.Result.ofOutput(ctx.newValueString(value)));
            break;
          case "explanation":
            matchBuilder
                .result()
                .filter(result -> result.kind().equals(Match.Result.Kind.RULE))
                .ifPresent(
                    result ->
                        ctx.reportError(
                            tagId,
                            "Explanation can only be set on output match cases, not nested rules"));
            matchBuilder.setExplanation(ctx.newValueString(value));
            break;
          case "rule":
            matchBuilder
                .result()
                .filter(result -> result.kind().equals(Match.Result.Kind.OUTPUT))
                .ifPresent(
                    result -> ctx.reportError(tagId, "Only the rule or the output may be set"));
            matchBuilder
                .explanation()
                .ifPresent(
                    result ->
                        ctx.reportError(
                            result.id(),
                            "Explanation can only be set on output match cases, not nested rules"));
            matchBuilder.setResult(Match.Result.ofRule(parseRule(ctx, policyBuilder, value)));
            break;
          default:
            tagVisitor.visitMatchTag(ctx, tagId, fieldName, value, policyBuilder, matchBuilder);
            break;
        }
      }

      if (!assertRequiredFields(ctx, nodeId, matchBuilder.getMissingRequiredFieldNames())) {
        return ERROR_MATCH;
      }

      return matchBuilder.build();
    }

    private ImmutableSet<CelPolicy.Variable> parseVariables(
        PolicyParserContext<Node> ctx, CelPolicy.Builder policyBuilder, Node node) {
      long valueId = ctx.collectMetadata(node);
      ImmutableSet.Builder<CelPolicy.Variable> variableBuilder = ImmutableSet.builder();
      if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
        return variableBuilder.build();
      }

      SequenceNode variableListNode = (SequenceNode) node;
      for (Node elementNode : variableListNode.getValue()) {
        variableBuilder.add(parseVariable(ctx, policyBuilder, elementNode));
      }

      return variableBuilder.build();
    }

    @Override
    public CelPolicy.Variable parseVariable(
        PolicyParserContext<Node> ctx, CelPolicy.Builder policyBuilder, Node node) {
      long id = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
        return ERROR_VARIABLE;
      }
      MappingNode variableMap = (MappingNode) node;
      Variable.Builder builder = Variable.newBuilder();

      for (NodeTuple nodeTuple : variableMap.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        long keyId = ctx.collectMetadata(keyNode);
        Node valueNode = nodeTuple.getValueNode();
        String keyName = ((ScalarNode) keyNode).getValue();
        switch (keyName) {
          case "name":
            builder.setName(ctx.newValueString(valueNode));
            break;
          case "expression":
            builder.setExpression(ctx.newValueString(valueNode));
            break;
          default:
            tagVisitor.visitVariableTag(ctx, keyId, keyName, valueNode, policyBuilder, builder);
            break;
        }
      }

      if (!assertRequiredFields(ctx, id, builder.getMissingRequiredFieldNames())) {
        return ERROR_VARIABLE;
      }

      return builder.build();
    }

    private ParserImpl(TagVisitor<Node> tagVisitor, String source, String description) {
      this.tagVisitor = tagVisitor;
      this.policySource =
          CelPolicySource.newBuilder(CelCodePointArray.fromString(source))
              .setDescription(description)
              .build();
      this.ctx = YamlParserContextImpl.newInstance(policySource);
    }

    @Override
    public long nextId() {
      return ctx.nextId();
    }

    @Override
    public long collectMetadata(Node node) {
      return ctx.collectMetadata(node);
    }

    @Override
    public void reportError(long id, String message) {
      ctx.reportError(id, message);
    }

    @Override
    public List<CelIssue> getIssues() {
      return ctx.getIssues();
    }

    @Override
    public Map<Long, Integer> getIdToOffsetMap() {
      return ctx.getIdToOffsetMap();
    }

    @Override
    public ValueString newValueString(Node node) {
      return ctx.newValueString(node);
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
