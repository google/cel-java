package dev.cel.policy;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelSource;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.ValueString;
import dev.cel.policy.CelPolicy.Variable;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.io.StringReader;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

final class CelPolicyYamlParser implements CelPolicyParser {

  private static final String ERROR = "*error*";
  private final TagVisitor<Node> tagVisitor;

  @Override
  public CelPolicy parse(CelPolicySource source) throws CelPolicyValidationException {
    ParserImpl parser = new ParserImpl(tagVisitor);
    return parser.parseYaml(source);
  }

  private static class ParserImpl {

    private final TagVisitor<Node> tagVisitor;

    private CelPolicy parseYaml(CelPolicySource source) throws CelPolicyValidationException {
      Node node;
      try {
        node = parseYamlSource(source);
      } catch (Exception e) {
        throw new CelPolicyValidationException(
            "YAML document is malformed: " + e.getMessage(), e);
      }

      ParserContextImpl ctx = ParserContextImpl.newInstance(source);
      CelPolicy.Builder policyBuilder = CelPolicy.newBuilder()
          .setPolicySource(source)
          .setCelSource(fromPolicySource(source));

      CelPolicy policy = parsePolicy(ctx, policyBuilder, node);

      if (ctx.hasError()) {
        throw new CelPolicyValidationException(ctx.getIssueString());
      }

      return policy;
    }

    private CelPolicy parsePolicy(ParserContextImpl ctx, CelPolicy.Builder policyBuilder,
        Node node) {
      long id = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
        return policyBuilder.build();
      }

      MappingNode rootNode = (MappingNode) node;
      for (NodeTuple nodeTuple : rootNode.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        Node valueNode = nodeTuple.getValueNode();
        long keyId = ctx.collectMetadata(keyNode);
        if (!assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING, YamlNodeType.TEXT)) {
          continue;
        }
        String fieldName = ((ScalarNode) keyNode).getValue();
        switch (fieldName) {
          case "name":
            policyBuilder.setName(newString(ctx, valueNode));
            break;
          case "rule":
            policyBuilder.setRule(parseRule(ctx, valueNode));
            break;
          default:
            tagVisitor.visitPolicyTag(ctx, keyId, fieldName, valueNode, policyBuilder);
            break;
        }
      }

      return policyBuilder.build();
    }

    private boolean assertYamlType(ParserContextImpl ctx, long id, Node node,
        YamlNodeType... expectedNodeTypes) {
      String nodeTag = node.getTag().getValue();
      for (YamlNodeType expectedNodeType : expectedNodeTypes) {
        if (expectedNodeType.tag().equals(nodeTag)) {
          return true;
        }
      }
      ctx.reportError(id, String.format("Got yaml node type %s, wanted type(s) [%s]", nodeTag,
          Arrays.stream(expectedNodeTypes).map(YamlNodeType::tag)
              .collect(Collectors.joining(" "))));
      return false;
    }

    private Node parseYamlSource(CelPolicySource policySource) {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

      return yaml.compose(new StringReader(policySource.content().toString()));
    }

    private CelPolicy.Rule parseRule(ParserContextImpl ctx, Node node) {
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
            ruleBuilder.setId(newString(ctx, value));
            break;
          case "description":
            ruleBuilder.setDescription(newString(ctx, value));
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

    private ImmutableSet<CelPolicy.Match> parseMatches(ParserContextImpl ctx, Node node) {
      long valueId = ctx.collectMetadata(node);
      ImmutableSet.Builder<CelPolicy.Match> matchesBuilder = ImmutableSet.builder();
      if (!assertYamlType(ctx, valueId, node, YamlNodeType.LIST)) {
        return matchesBuilder.build();
      }

      SequenceNode matchListNode = (SequenceNode) node;
      for (Node elementNode : matchListNode.getValue()) {
        long id = ctx.collectMetadata(elementNode);
        if (!assertYamlType(ctx, id, elementNode, YamlNodeType.MAP)) {
          continue;
        }
        matchesBuilder.add(parseMatch(ctx, (MappingNode) elementNode));
      }

      return matchesBuilder.build();
    }

    private CelPolicy.Match parseMatch(ParserContextImpl ctx, MappingNode node) {
      CelPolicy.Match.Builder matchBuilder = CelPolicy.Match.newBuilder();
      for (NodeTuple nodeTuple : node.getValue()) {
        Node key = nodeTuple.getKeyNode();
        long tagId = ctx.collectMetadata(key);
        if (!assertYamlType(ctx, tagId, key, YamlNodeType.STRING, YamlNodeType.TEXT)) {
          continue;
        }
        String fieldName = ((ScalarNode) key).getValue();
        Node value = nodeTuple.getValueNode();
        switch (fieldName) {
          case "condition":
            matchBuilder.setCondition(newString(ctx, value));
            break;
          case "output":
            matchBuilder.result()
                .filter(result -> result.kind().equals(Match.Result.Kind.RULE))
                .ifPresent(
                    result -> ctx.reportError(tagId, "Only the rule or the output may be set"));
            matchBuilder.setResult(Match.Result.ofOutput(newString(ctx, value)));
            break;
          case "rule":
            matchBuilder.result()
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
      return matchBuilder.build();
    }

    private ImmutableSet<CelPolicy.Variable> parseVariables(ParserContextImpl ctx, Node node) {
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

    private CelPolicy.Variable parseVariable(ParserContextImpl ctx, MappingNode variableMap) {
      Variable.Builder builder = Variable.newBuilder();

      for (NodeTuple nodeTuple : variableMap.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        long keyId = ctx.collectMetadata(keyNode);
        Node valueNode = nodeTuple.getValueNode();
        String keyName = ((ScalarNode) keyNode).getValue();
        switch (keyName) {
          case "name":
            builder.setName(newString(ctx, valueNode));
            break;
          case "expression":
            builder.setExpression(newString(ctx, valueNode));
            break;
          default:
            tagVisitor.visitVariableTag(ctx, keyId, keyName, valueNode, builder);
            break;
        }
      }

      return builder.build();
    }

    private ValueString newString(ParserContextImpl ctx, Node node) {
      long id = ctx.collectMetadata(node);
      if (!assertYamlType(ctx, id, node, YamlNodeType.STRING, YamlNodeType.TEXT)) {
        return ValueString.of(id, ERROR);
      }

      return ValueString.of(id, ((ScalarNode) node).getValue());
    }

    private static CelSource fromPolicySource(CelPolicySource policySource) {
      // TODO: Overload for accepting code point directly?
      // TODO: Is this necessary?
      return CelSource.newBuilder(policySource.content().toString())
          .setDescription(policySource.description())
          .build();
    }

    private ParserImpl(TagVisitor<Node> tagVisitor) {
      this.tagVisitor = tagVisitor;
    }
  }


  static final class Builder implements CelPolicyParserBuilder<Node> {

    private TagVisitor<Node> tagVisitor;

    private Builder() {
      this.tagVisitor = new TagVisitor<Node>() {
      };
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

  private CelPolicyYamlParser(
      TagVisitor<Node> tagVisitor
  ) {
    this.tagVisitor = tagVisitor;
  }
}
