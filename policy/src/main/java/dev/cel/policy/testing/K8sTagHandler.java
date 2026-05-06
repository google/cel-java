// Copyright 2026 Google LLC
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

package dev.cel.policy.testing;

import com.google.common.annotations.VisibleForTesting;
import dev.cel.common.formats.ValueString;
import dev.cel.common.formats.YamlHelper;
import dev.cel.common.formats.YamlHelper.YamlNodeType;
import dev.cel.policy.CelPolicy;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicyParser.TagVisitor;
import dev.cel.policy.PolicyParserContext;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * K8sTagHandler is a {@link TagVisitor} implementation to support parsing Kubernetes
 * ValidatingAdmissionPolicy structures in testing and conformance environments.
 */
@VisibleForTesting
public final class K8sTagHandler implements TagVisitor<Node> {

  @Override
  public void visitPolicyTag(
      PolicyParserContext<Node> ctx,
      long id,
      String tagName,
      Node node,
      CelPolicy.Builder policyBuilder) {
    switch (tagName) {
      case "kind":
        policyBuilder.putMetadata("kind", ctx.newYamlString(node).value());
        break;
      case "metadata":
        YamlHelper.assertYamlType(ctx, id, node, YamlNodeType.MAP);
        break;
      case "spec":
        CelPolicy.Rule spec = ctx.parseRule(ctx, policyBuilder, node);
        policyBuilder.setRule(spec);
        break;
      default:
        TagVisitor.super.visitPolicyTag(ctx, id, tagName, node, policyBuilder);
        break;
    }
  }

  @Override
  public void visitRuleTag(
      PolicyParserContext<Node> ctx,
      long id,
      String tagName,
      Node node,
      CelPolicy.Builder policyBuilder,
      CelPolicy.Rule.Builder ruleBuilder) {
    switch (tagName) {
      case "failurePolicy":
        policyBuilder.putMetadata(tagName, ctx.newYamlString(node).value());
        break;
      case "matchConstraints":
        YamlHelper.assertYamlType(ctx, id, node, YamlNodeType.MAP);
        break;
      case "validations":
        if (!YamlHelper.assertYamlType(ctx, id, node, YamlNodeType.LIST)) {
          return;
        }
        SequenceNode seqNode = (SequenceNode) node;
        for (Node valNode : seqNode.getValue()) {
          ruleBuilder.addMatches(ctx.parseMatch(ctx, policyBuilder, valNode));
        }
        break;
      default:
        TagVisitor.super.visitRuleTag(ctx, id, tagName, node, policyBuilder, ruleBuilder);
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
      CelPolicy.Match.Builder matchBuilder) {
    if (!matchBuilder.result().isPresent()) {
      matchBuilder.setResult(
          Match.Result.ofOutput(ValueString.of(ctx.nextId(), "'invalid admission request'")));
    }
    switch (tagName) {
      case "expression":
        // The K8s expression to validate must return false in order to generate a violation
        // message.
        ValueString condition = ctx.newSourceString(node);
        String invertedCondition = "!(" + condition.value() + ")";
        matchBuilder.setCondition(ValueString.of(condition.id(), invertedCondition));
        break;
      case "messageExpression":
        matchBuilder.setResult(Match.Result.ofOutput(ctx.newSourceString(node)));
        break;
      default:
        TagVisitor.super.visitMatchTag(ctx, id, tagName, node, policyBuilder, matchBuilder);
        break;
    }
  }
}
