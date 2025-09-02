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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.io.Resources;
import dev.cel.common.formats.ValueString;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Match.Result;
import dev.cel.policy.CelPolicy.Rule;
import dev.cel.policy.CelPolicyParser.TagVisitor;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

/** Package-private class to assist with policy testing. */
final class PolicyTestHelper {

  enum TestYamlPolicy {
    NESTED_RULE(
        "nested_rule",
        true,
        "cel.@block([resource.origin, @index0 in [\"us\", \"uk\", \"es\"], {\"banned\": true}],"
            + " ((@index0 in {\"us\": false, \"ru\": false, \"ir\": false} && !@index1) ?"
            + " optional.of(@index2) : optional.none()).or(optional.of(@index1 ? {\"banned\":"
            + " false} : @index2)))"),
    NESTED_RULE2(
        "nested_rule2",
        false,
        "cel.@block([resource.origin, !(@index0 in [\"us\", \"uk\", \"es\"])],"
            + " resource.?user.orValue(\"\").startsWith(\"bad\") ? ((@index0 in {\"us\": false,"
            + " \"ru\": false, \"ir\": false} && @index1) ? {\"banned\": \"restricted_region\"} :"
            + " {\"banned\": \"bad_actor\"}) : (@index1 ? {\"banned\": \"unconfigured_region\"} :"
            + " {}))"),
    NESTED_RULE3(
        "nested_rule3",
        true,
        "cel.@block([resource.origin, !(@index0 in [\"us\", \"uk\", \"es\"])],"
            + " resource.?user.orValue(\"\").startsWith(\"bad\") ? optional.of((@index0 in {\"us\":"
            + " false, \"ru\": false, \"ir\": false} && @index1) ? {\"banned\":"
            + " \"restricted_region\"} : {\"banned\": \"bad_actor\"}) : (@index1 ?"
            + " optional.of({\"banned\": \"unconfigured_region\"}) : optional.none()))"),
    REQUIRED_LABELS(
        "required_labels",
        true,
        "cel.@block([spec.labels.filter(@it:0:0, !(@it:0:0 in resource.labels)), spec.labels,"
            + " resource.labels, @index2.filter(@it:0:0, @it:0:0 in @index1 && @index1[@it:0:0] !="
            + " @index2[@it:0:0])], (@index0.size() > 0) ? optional.of(\"missing one or more"
            + " required labels: [\"\" + @index0.join(\",\") + \"\"]\") : ((@index3.size() > 0) ?"
            + " optional.of(\"invalid values provided on one or more labels: [\"\" +"
            + " @index3.join(\",\") + \"\"]\") : optional.none()))"),
    RESTRICTED_DESTINATIONS(
        "restricted_destinations",
        false,
        "cel.@block([request.auth.claims, has(@index0.nationality), resource.labels.location in"
            + " spec.restricted_destinations], (@index1 && @index0.nationality == spec.origin &&"
            + " (locationCode(destination.ip) in spec.restricted_destinations || @index2)) ? true :"
            + " ((!@index1 && locationCode(origin.ip) == spec.origin &&"
            + " (locationCode(destination.ip) in spec.restricted_destinations || @index2)) ? true :"
            + " false))"),
    K8S(
        "k8s",
        true,
        "cel.@block([resource.labels.?environment.orValue(\"prod\")],"
            + " !(resource.labels.?break_glass.orValue(\"false\") == \"true\" ||"
            + " resource.containers.all(@it:0:0, @it:0:0.startsWith(@index0 + \".\"))) ?"
            + " optional.of(\"only \" + @index0 + \" containers are allowed in namespace \" +"
            + " resource.namespace) : optional.none())"),
    PB(
        "pb",
        true,
        "cel.@block([spec.single_int32], (@index0 > 10) ? optional.of(\"invalid spec, got"
            + " single_int32=\" + string(@index0) + \", wanted <= 10\") : ((spec.standalone_enum =="
            + " cel.expr.conformance.proto3.TestAllTypes.NestedEnum.BAR ||"
            + " dev.cel.testing.testdata.proto3.StandaloneGlobalEnum.SGAR =="
            + " dev.cel.testing.testdata.proto3.StandaloneGlobalEnum.SGOO) ? optional.of(\"invalid"
            + " spec, neither nested nor imported enums may refer to BAR\") : optional.none()))"),
    LIMITS(
        "limits",
        true,
        "cel.@block([now.getHours()], (@index0 >= 20) ? ((@index0 < 21) ? optional.of(\"goodbye,"
            + " me!\") : ((@index0 < 22) ? optional.of(\"goodbye, me!!\") : ((@index0 < 24) ?"
            + " optional.of(\"goodbye, me!!!\") : optional.none()))) : optional.of(\"hello,"
            + " me\"))");

    private final String name;
    private final boolean producesOptionalResult;
    private final String unparsed;

    TestYamlPolicy(String name, boolean producesOptionalResult, String unparsed) {
      this.name = name;
      this.producesOptionalResult = producesOptionalResult;
      this.unparsed = unparsed;
    }

    String getPolicyName() {
      return name;
    }

    boolean producesOptionalResult() {
      return this.producesOptionalResult;
    }

    String getUnparsed() {
      return unparsed;
    }

    String readPolicyYamlContent() throws IOException {
      return readFromYaml(String.format("policy/%s/policy.yaml", name));
    }

    String readConfigYamlContent() throws IOException {
      return readFromYaml(String.format("policy/%s/config.yaml", name));
    }

    PolicyTestSuite readTestYamlContent() throws IOException {
      Yaml yaml = new Yaml(new Constructor(PolicyTestSuite.class, new LoaderOptions()));
      String testContent = readFile(String.format("policy/%s/tests.yaml", name));

      return yaml.load(testContent);
    }
  }

  static String readFromYaml(String yamlPath) throws IOException {
    return readFile(yamlPath);
  }

  /**
   * TestSuite describes a set of tests divided by section.
   *
   * <p>Visibility must be public for YAML deserialization to work. This is effectively
   * package-private since the outer class is.
   */
  @VisibleForTesting
  public static final class PolicyTestSuite {
    private String description;
    private List<PolicyTestSection> section;

    public void setDescription(String description) {
      this.description = description;
    }

    public void setSection(List<PolicyTestSection> section) {
      this.section = section;
    }

    public String getDescription() {
      return description;
    }

    public List<PolicyTestSection> getSection() {
      return section;
    }

    @VisibleForTesting
    public static final class PolicyTestSection {
      private String name;
      private List<PolicyTestCase> tests;

      public void setName(String name) {
        this.name = name;
      }

      public void setTests(List<PolicyTestCase> tests) {
        this.tests = tests;
      }

      public String getName() {
        return name;
      }

      public List<PolicyTestCase> getTests() {
        return tests;
      }

      @VisibleForTesting
      public static final class PolicyTestCase {
        private String name;
        private Map<String, PolicyTestInput> input;
        private String output;

        public void setName(String name) {
          this.name = name;
        }

        public void setInput(Map<String, PolicyTestInput> input) {
          this.input = input;
        }

        public void setOutput(String output) {
          this.output = output;
        }

        public String getName() {
          return name;
        }

        public Map<String, PolicyTestInput> getInput() {
          return input;
        }

        public String getOutput() {
          return output;
        }

        @VisibleForTesting
        public static final class PolicyTestInput {
          private Object value;
          private String expr;

          public Object getValue() {
            return value;
          }

          public void setValue(Object value) {
            this.value = value;
          }

          public String getExpr() {
            return expr;
          }

          public void setExpr(String expr) {
            this.expr = expr;
          }
        }
      }
    }
  }

  private static URL getResource(String path) {
    return Resources.getResource(Ascii.toLowerCase(path));
  }

  private static String readFile(String path) throws IOException {
    return Resources.toString(getResource(path), UTF_8);
  }

  static class K8sTagHandler implements TagVisitor<Node> {

    @Override
    public void visitPolicyTag(
        PolicyParserContext<Node> ctx,
        long id,
        String tagName,
        Node node,
        CelPolicy.Builder policyBuilder) {
      switch (tagName) {
        case "kind":
          policyBuilder.putMetadata("kind", ctx.newValueString(node));
          break;
        case "metadata":
          long metadataId = ctx.collectMetadata(node);
          if (!node.getTag().getValue().equals("tag:yaml.org,2002:map")) {
            ctx.reportError(
                metadataId,
                String.format(
                    "invalid 'metadata' type, expected map got: %s", node.getTag().getValue()));
          }
          break;
        case "spec":
          Rule rule = ctx.parseRule(ctx, policyBuilder, node);
          policyBuilder.setRule(rule);
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
        Rule.Builder ruleBuilder) {
      switch (tagName) {
        case "failurePolicy":
          policyBuilder.putMetadata(tagName, ctx.newValueString(node));
          break;
        case "matchConstraints":
          long matchConstraintsId = ctx.collectMetadata(node);
          if (!node.getTag().getValue().equals("tag:yaml.org,2002:map")) {
            ctx.reportError(
                matchConstraintsId,
                String.format(
                    "invalid 'matchConstraints' type, expected map got: %s",
                    node.getTag().getValue()));
          }
          break;
        case "validations":
          long validationId = ctx.collectMetadata(node);
          if (!node.getTag().getValue().equals("tag:yaml.org,2002:seq")) {
            ctx.reportError(
                validationId,
                String.format(
                    "invalid 'validations' type, expected list got: %s", node.getTag().getValue()));
          }

          SequenceNode validationNodes = (SequenceNode) node;
          for (Node element : validationNodes.getValue()) {
            ruleBuilder.addMatches(ctx.parseMatch(ctx, policyBuilder, element));
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
        Match.Builder matchBuilder) {
      switch (tagName) {
        case "expression":
          // The K8s expression to validate must return false in order to generate a violation
          // message.
          ValueString conditionValue = ctx.newValueString(node);
          conditionValue =
              conditionValue.toBuilder().setValue("!(" + conditionValue.value() + ")").build();
          matchBuilder.setCondition(conditionValue);
          break;
        case "messageExpression":
          matchBuilder.setResult(Result.ofOutput(ctx.newValueString(node)));
          break;
        default:
          TagVisitor.super.visitMatchTag(ctx, id, tagName, node, policyBuilder, matchBuilder);
          break;
      }
    }
  }

  private PolicyTestHelper() {}
}
