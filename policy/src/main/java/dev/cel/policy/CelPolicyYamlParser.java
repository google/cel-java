package dev.cel.policy;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSource;
import dev.cel.common.CelSourceLocation;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Rule;
import dev.cel.policy.CelPolicy.ValueString;
import dev.cel.policy.CelPolicy.Variable;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
        throw new CelPolicyValidationException("Failure parsing YAML document.", e);
      }

      ParserContextImpl ctx = new ParserContextImpl(source);
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
        if (!assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING)) {
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

      // TODO: Add test for disallowing multple yaml doc

      // Object test = yaml.load(policySource.content());
      //
      // return (Map<String, Object>) test;
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
        if (!assertYamlType(ctx, tagId, key, YamlNodeType.STRING)) {
          continue;
        }
        String fieldName = ((ScalarNode) key).getValue();
        Node value = nodeTuple.getValueNode();
        switch (fieldName) {
          case "id":
            ruleBuilder.setId(newString(ctx, value));
            break;
          case "description":
            break;
          case "variables":
            ruleBuilder.addVariables(parseVariables(ctx, value));
            break;
          case "match":
            break;
          default:
            tagVisitor.visitRuleTag(ctx, tagId, fieldName, value, ruleBuilder);
            break;
        }
      }
      return ruleBuilder.build();
    }


    private ImmutableSet<CelPolicy.Variable> parseVariables(ParserContextImpl ctx,
        Node node) {
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

  private static class ParserContextImpl implements ParserContext {

    private static final Joiner JOINER = Joiner.on('\n');

    private final ArrayList<CelIssue> issues;
    private final HashMap<Long, CelSourceLocation> idToLocationMap;
    private final CelPolicySource source;
    private long id;

    @Override
    public void reportError(long id, String message) {
      issues.add(CelIssue.formatError(idToLocationMap.get(id), message));
    }

    private String getIssueString() {
      return JOINER.join(
          issues.stream().map(iss -> iss.toDisplayString(source))
              .collect(toImmutableList()));
    }

    private boolean hasError() {
      return !issues.isEmpty();
    }

    private long collectMetadata(Node node) {
      long id = nextId();
      int line = node.getStartMark().getLine() + 1; // Yaml lines are 0 indexed
      int column = node.getStartMark().getColumn();
      idToLocationMap.put(id, CelSourceLocation.of(line, column));

      return id;
    }

    private long nextId() {
      return ++id;
    }

    private ParserContextImpl(CelPolicySource policySource) {
      this.issues = new ArrayList<>();
      this.idToLocationMap = new HashMap<>();
      this.source = policySource;
    }
  }

  private static class DefaultTagVisitor implements TagVisitor<Node> {

    @Override
    public void visitPolicyTag(ParserContext ctx, long id, String fieldName, Node node,
        CelPolicy.Builder policyBuilder) {
      ctx.reportError(id, String.format("Unsupported policy tag: %s", fieldName));
    }

    @Override
    public void visitRuleTag(ParserContext ctx, long id, String fieldName, Node node,
        Rule.Builder ruleBuilder) {
      ctx.reportError(id, String.format("Unsupported rule tag: %s", fieldName));
    }

    @Override
    public void visitMatchTag(ParserContext ctx, long id, String fieldName, Node node,
        Match.Builder matchBuilder) {
      ctx.reportError(id, String.format("Unsupported match tag: %s", fieldName));
    }

    @Override
    public void visitVariableTag(ParserContext ctx, long id, String fieldName, Node node,
        Variable.Builder variableBuilder) {
      ctx.reportError(id, String.format("Unsupported variable tag: %s", fieldName));
    }
  }

  static CelPolicyParser newInstance() {
    return new CelPolicyYamlParser(new DefaultTagVisitor());
  }

  private CelPolicyYamlParser(
      TagVisitor<Node> tagVisitor
  ) {
    this.tagVisitor = tagVisitor;
  }
}
