package dev.cel.tools.ai;

import static dev.cel.common.formats.YamlHelper.assertYamlType;

import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.formats.ValueString;
import dev.cel.common.formats.YamlHelper.YamlNodeType;
import dev.cel.policy.CelPolicy;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Match.Result;
import dev.cel.policy.CelPolicy.Rule;
import dev.cel.policy.CelPolicy.Variable;
import dev.cel.policy.CelPolicyCompiler;
import dev.cel.policy.CelPolicyCompilerFactory;
import dev.cel.policy.CelPolicyParser;
import dev.cel.policy.CelPolicyParser.TagVisitor;
import dev.cel.policy.CelPolicyParserFactory;
import dev.cel.policy.CelPolicyValidationException;
import dev.cel.policy.PolicyParserContext;
import java.util.ArrayList;
import java.util.List;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public final class AgenticPolicyCompiler {

  private static final CelPolicyParser POLICY_PARSER =
      CelPolicyParserFactory.newYamlParserBuilder()
          .addTagVisitor(new AgenticPolicyTagHandler())
          .build();

  private final CelPolicyCompiler policyCompiler;

  public static AgenticPolicyCompiler newInstance(Cel cel) {
    return new AgenticPolicyCompiler(cel);
  }

  private AgenticPolicyCompiler(Cel cel) {
    this.policyCompiler = CelPolicyCompilerFactory.newPolicyCompiler(cel).build();
  }

  public CelAbstractSyntaxTree compile(String policySource) throws CelPolicyValidationException {
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    return policyCompiler.compile(policy);
  }

  private static class AgenticPolicyTagHandler implements TagVisitor<Node> {

    @Override
    public void visitPolicyTag(
        PolicyParserContext<Node> ctx,
        long id,
        String tagName,
        Node node,
        CelPolicy.Builder policyBuilder) {

      switch (tagName) {
        case "default":
          if (assertYamlType(ctx, id, node, YamlNodeType.STRING)) {
            policyBuilder.putMetadata("default_effect", ((ScalarNode) node).getValue());
          }
          break;

        case "variables":
          if (!assertYamlType(ctx, id, node, YamlNodeType.LIST)) {
            return;
          }
          List<Variable> parsedVariables = new ArrayList<>();
          SequenceNode varList = (SequenceNode) node;

          for (Node varNode : varList.getValue()) {
            if (assertYamlType(ctx, ctx.collectMetadata(varNode), varNode, YamlNodeType.MAP)) {
              MappingNode map = (MappingNode) varNode;
              for (NodeTuple tuple : map.getValue()) {
                String name = ((ScalarNode) tuple.getKeyNode()).getValue();
                String expr = ((ScalarNode) tuple.getValueNode()).getValue();
                parsedVariables.add(Variable.newBuilder()
                    .setName(ValueString.of(ctx.collectMetadata(tuple.getKeyNode()), name))
                    .setExpression(ValueString.of(ctx.collectMetadata(tuple.getValueNode()), expr))
                    .build());
              }
            }
          }
          policyBuilder.putMetadata("top_level_variables", parsedVariables);
          break;

        case "rules":
          if (!assertYamlType(ctx, id, node, YamlNodeType.LIST)) return;
          SequenceNode rulesNode = (SequenceNode) node;
          Rule.Builder subRuleBuilder = Rule.newBuilder(ctx.collectMetadata(rulesNode));

          if (policyBuilder.metadata().containsKey("top_level_variables")) {
            List<Variable> variables = (List<Variable>) policyBuilder.metadata().get("top_level_variables");
            subRuleBuilder.addVariables(variables);
          }

          for (Node ruleNode : rulesNode.getValue()) {
            policyBuilder.putMetadata("effect", "deny");
            policyBuilder.putMetadata("message", "");
            policyBuilder.putMetadata("output_expr", null);

            Match subMatch = ctx.parseMatch(ctx, policyBuilder, ruleNode);
            subRuleBuilder.addMatches(subMatch);
          }

          if (policyBuilder.metadata().containsKey("default_effect")) {
            String defaultEffect = policyBuilder.metadata().get("default_effect").toString();
            Match defaultMatch = Match.newBuilder(ctx.nextId())
                .setCondition(ValueString.of(ctx.nextId(), "true"))
                .setResult(Result.ofOutput(ValueString.of(ctx.nextId(), generateMessageOutput(defaultEffect, ""))))
                .build();
            subRuleBuilder.addMatches(defaultMatch);
          }
          policyBuilder.setRule(subRuleBuilder.build());
          break;

        default:
          TagVisitor.super.visitPolicyTag(ctx, id, tagName, node, policyBuilder);
          break;
      }
    }

    @Override
    public void visitMatchTag(
        PolicyParserContext<Node> ctx,
        long id,
        String tagName,
        Node node,
        CelPolicy.Builder policyBuilder,
        Match.Builder matchBuilder) {

      switch (tagName) {
        case "description":
          if (assertYamlType(ctx, id, node, YamlNodeType.STRING)) {
            matchBuilder.setExplanation(ValueString.of(ctx.nextId(), ((ScalarNode) node).getValue()));
          }
          break;

        case "effect":
        case "message":
        case "output_expr":
          if (!assertYamlType(ctx, id, node, YamlNodeType.STRING)) return;

          String value = ((ScalarNode) node).getValue();
          policyBuilder.putMetadata(tagName, value);

          String currentEffect = (String) policyBuilder.metadata().get("effect");
          String currentMessage = (String) policyBuilder.metadata().get("message");
          String currentOutputExpr = (String) policyBuilder.metadata().get("output_expr");

          String finalOutput = (currentOutputExpr != null)
              ? generateDetailsOutput(currentEffect, currentOutputExpr)
              : generateMessageOutput(currentEffect, currentMessage);

          matchBuilder.setResult(Result.ofOutput(ValueString.of(ctx.nextId(), finalOutput)));
          break;

        default:
          TagVisitor.super.visitMatchTag(ctx, id, tagName, node, policyBuilder, matchBuilder);
          break;
      }
    }

    // The following will likely benefit from having a concrete output structure
    private static String generateMessageOutput(String effect, String message) {
      String safeMessage = message.replace("'", "\\'");
      return String.format("{'effect': '%s', 'message': '%s'}", effect, safeMessage);
    }

    private static String generateDetailsOutput(String effect, String outputExpression) {
      return String.format("{'effect': '%s', 'details': %s}", effect, outputExpression);
    }
  }
}
