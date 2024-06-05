package dev.cel.policy;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSource;
import dev.cel.common.CelSourceLocation;
import dev.cel.policy.CelPolicy.ValueString;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
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

  private static class ParserContext {

    private static final Joiner JOINER = Joiner.on('\n');

    private final ArrayList<CelIssue> issues;
    private final HashMap<Long, CelSourceLocation> idToLocationMap;
    private final CelPolicySource.Builder sourceBuilder;
    private long id;

    private void reportError(long id, String message) {
      issues.add(CelIssue.formatError(idToLocationMap.get(id), message));
    }

    private String getIssueString() {
      return JOINER.join(
          Iterables.transform(issues, iss -> iss.toDisplayString(sourceBuilder.build())));
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

    private ParserContext(CelPolicySource policySource) {
      this.issues = new ArrayList<>();
      this.idToLocationMap = new HashMap<>();
      this.sourceBuilder = policySource.toBuilder();
    }
  }

  private static class ParserImpl {

    private CelPolicy parseYaml(CelPolicySource source) throws CelPolicyValidationException {
      Node node;
      try {
        node = parseYamlSource(source);
      } catch (Exception e) {
        throw new CelPolicyValidationException("Failure parsing YAML document.", e);
      }

      ParserContext ctx = new ParserContext(source);
      long id = ctx.collectMetadata(node);
      assertYamlType(ctx, id, node, YamlNodeType.MAP);
      return parsePolicy(ctx, (MappingNode) node);
    }

    private CelPolicy parsePolicy(ParserContext ctx, MappingNode rootNode)
        throws CelPolicyValidationException {
      CelPolicy.Builder policyBuilder = CelPolicy.newBuilder();
      for (NodeTuple nodeTuple : rootNode.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        Node valueNode = nodeTuple.getValueNode();
        long keyId = ctx.collectMetadata(keyNode);
        long valueId = ctx.collectMetadata(valueNode);
        if (!assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING)) {
          continue;
        }
        String fieldName = ((ScalarNode) keyNode).getValue();
        switch (fieldName) {
          case "name":
            if (!assertYamlType(ctx, valueId, valueNode, YamlNodeType.STRING)) {
              break;
            }
            String policyName = ((ScalarNode) valueNode).getValue();
            policyBuilder.setName(CelPolicy.ValueString.of(valueId, policyName));
            break;
          case "rule":
            if (!assertYamlType(ctx, valueId, valueNode, YamlNodeType.MAP)) {
              break;
            }
            policyBuilder.setRule(parseRule(ctx, (MappingNode) valueNode));
            break;
        }
      }

      if (ctx.hasError()) {
        String formattedError = ctx.getIssueString();
        throw new CelPolicyValidationException(formattedError);
      }

      CelPolicySource policySource = ctx.sourceBuilder.build();
      return policyBuilder
          .setPolicySource(policySource)
          .setCelSource(fromPolicySource(policySource))
          .build();
    }

    private boolean assertYamlType(ParserContext ctx, long id, Node node,
        YamlNodeType expectedNodeType) {
      String nodeTag = node.getTag().getValue();
      if (!expectedNodeType.tag().equals(nodeTag)) {
        ctx.reportError(id, String.format("Got yaml node type %s, wanted type(s) %s", nodeTag,
            expectedNodeType.tag()));
        return false;
      }
      return true;
    }

    private Node parseYamlSource(CelPolicySource policySource) {

      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

      return yaml.compose(new StringReader(policySource.content().toString()));

      // TODO: Add test for disallowing multple yaml doc

      // Object test = yaml.load(policySource.content());
      //
      // return (Map<String, Object>) test;
    }

    private CelPolicy.Rule parseRule(ParserContext ctx, MappingNode ruleNode) {
      CelPolicy.Rule.Builder ruleBuilder = CelPolicy.Rule.newBuilder();
      for (NodeTuple nodeTuple : ruleNode.getValue()) {
        Node key = nodeTuple.getKeyNode();
        long keyId = ctx.collectMetadata(key);
        if (!assertYamlType(ctx, keyId, key, YamlNodeType.STRING)) {
          continue;
        }
        ScalarNode stringKeyNode = (ScalarNode) key;
        Node value = nodeTuple.getValueNode();
        long valueId = ctx.collectMetadata(nodeTuple.getValueNode());
        switch (stringKeyNode.getValue()) {
          case "id":
            if (assertYamlType(ctx, valueId, value, YamlNodeType.STRING)) {
              ruleBuilder.setId(CelPolicy.ValueString.of(valueId, ((ScalarNode) value).getValue()));
            }
            break;
          case "description":
            break;
          case "variables":
            if (assertYamlType(ctx, valueId, value, YamlNodeType.LIST)) {
              ruleBuilder.addVariables(parseVariables(ctx, (SequenceNode) value));
            }
            break;
          case "match":
            break;
          default:
            break;
        }
      }
      return ruleBuilder.build();
    }


    private ImmutableSet<CelPolicy.Variable> parseVariables(ParserContext ctx,
        SequenceNode variableListNode) {
      ImmutableSet.Builder<CelPolicy.Variable> variableBuilder = ImmutableSet.builder();
      for (Node node : variableListNode.getValue()) {
        long id = ctx.collectMetadata(node);
        if (!assertYamlType(ctx, id, node, YamlNodeType.MAP)) {
          continue;
        }
        variableBuilder.add(parseVariable(ctx, (MappingNode) node));
      }

      return variableBuilder.build();
    }

    private CelPolicy.Variable parseVariable(ParserContext ctx, MappingNode variableMap) {
      ValueString.Builder nameBuilder = ValueString.newBuilder();
      ValueString.Builder expressionBuilder = ValueString.newBuilder();
      for (NodeTuple nodeTuple : variableMap.getValue()) {
        Node keyNode = nodeTuple.getKeyNode();
        long keyId = ctx.collectMetadata(keyNode);
        Node valueNode = nodeTuple.getValueNode();
        long valueId = ctx.collectMetadata(valueNode);
        if (assertYamlType(ctx, keyId, keyNode, YamlNodeType.STRING) &&
            assertYamlType(ctx, valueId, valueNode, YamlNodeType.STRING)) {
          switch (((ScalarNode) keyNode).getValue()) {
            case "name":
              nameBuilder.setId(keyId);
              nameBuilder.setValue(((ScalarNode) valueNode).getValue());
              break;
            case "expression":
              expressionBuilder.setId(keyId);
              expressionBuilder.setValue(((ScalarNode) valueNode).getValue());
            default:
              // report error
          }
        }
      }
      return CelPolicy.Variable.of(
          nameBuilder.build(),
          expressionBuilder.build()
      );
    }

    private ValueString newString(ParserContext ctx, Node node) {
      long id = ctx.collectMetadata(node);
      if (assertYamlType(ctx, id, node, YamlNodeType.STRING)) {
        return ValueString.of(id, ERROR);
      }

      return ValueString.of(id, ((ScalarNode) node).getValue());
    }

    private static CelSource fromPolicySource(CelPolicySource policySource) {
      // TODO: Overload for accepting code point directly?
      return CelSource.newBuilder(policySource.content().toString())
          .setDescription(policySource.description())
          .build();
    }
  }

  @Override
  public CelPolicy parse(CelPolicySource source) throws CelPolicyValidationException {
    ParserImpl parser = new ParserImpl();
    return parser.parseYaml(source);
  }

  static CelPolicyParser newInstance() {
    return new CelPolicyYamlParser();
  }

  private CelPolicyYamlParser() {
  }
}
