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
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Match.Result;
import dev.cel.policy.CelPolicy.Rule;
import dev.cel.policy.CelPolicyParser.TagVisitor;
import dev.cel.policy.ParserContext.PolicyParserContext;
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
        "cel.bind(variables.permitted_regions, [\"us\", \"uk\", \"es\"],"
            + " cel.bind(variables.banned_regions, {\"us\": false, \"ru\": false, \"ir\": false},"
            + " (resource.origin in variables.banned_regions && "
            + "!(resource.origin in variables.permitted_regions)) "
            + "? optional.of({\"banned\": true}) : optional.none()).or("
            + "optional.of((resource.origin in variables.permitted_regions)"
            + " ? {\"banned\": false} : {\"banned\": true})))"),
    NESTED_RULE2(
        "nested_rule2",
        false,
        "cel.bind(variables.permitted_regions, [\"us\", \"uk\", \"es\"],"
            + " resource.?user.orValue(\"\").startsWith(\"bad\") ?"
            + " cel.bind(variables.banned_regions, {\"us\": false, \"ru\": false, \"ir\": false},"
            + " (resource.origin in variables.banned_regions && !(resource.origin in"
            + " variables.permitted_regions)) ? {\"banned\": \"restricted_region\"} : {\"banned\":"
            + " \"bad_actor\"}) : (!(resource.origin in variables.permitted_regions) ? {\"banned\":"
            + " \"unconfigured_region\"} : {}))"),
    NESTED_RULE3(
        "nested_rule3",
        true,
        "cel.bind(variables.permitted_regions, [\"us\", \"uk\", \"es\"],"
            + " resource.?user.orValue(\"\").startsWith(\"bad\") ?"
            + " optional.of(cel.bind(variables.banned_regions, {\"us\": false, \"ru\": false,"
            + " \"ir\": false}, (resource.origin in variables.banned_regions && !(resource.origin"
            + " in variables.permitted_regions)) ? {\"banned\": \"restricted_region\"} :"
            + " {\"banned\": \"bad_actor\"})) : (!(resource.origin in variables.permitted_regions)"
            + " ? optional.of({\"banned\": \"unconfigured_region\"}) : optional.none()))"),
    REQUIRED_LABELS(
        "required_labels",
        true,
        ""
            + "cel.bind(variables.want, spec.labels, cel.bind(variables.missing, "
            + "variables.want.filter(l, !(l in resource.labels)), cel.bind(variables.invalid, "
            + "resource.labels.filter(l, l in variables.want && variables.want[l] != "
            + "resource.labels[l]), (variables.missing.size() > 0) ? "
            + "optional.of(\"missing one or more required labels: [\"\" + "
            + "variables.missing.join(\",\") + \"\"]\") : ((variables.invalid.size() > 0) ? "
            + "optional.of(\"invalid values provided on one or more labels: [\"\" + "
            + "variables.invalid.join(\",\") + \"\"]\") : optional.none()))))"),
    RESTRICTED_DESTINATIONS(
        "restricted_destinations",
        false,
        "cel.bind(variables.matches_origin_ip, locationCode(origin.ip) == spec.origin,"
            + " cel.bind(variables.has_nationality, has(request.auth.claims.nationality),"
            + " cel.bind(variables.matches_nationality, variables.has_nationality &&"
            + " request.auth.claims.nationality == spec.origin, cel.bind(variables.matches_dest_ip,"
            + " locationCode(destination.ip) in spec.restricted_destinations,"
            + " cel.bind(variables.matches_dest_label, resource.labels.location in"
            + " spec.restricted_destinations, cel.bind(variables.matches_dest,"
            + " variables.matches_dest_ip || variables.matches_dest_label,"
            + " (variables.matches_nationality && variables.matches_dest) ? true :"
            + " ((!variables.has_nationality && variables.matches_origin_ip &&"
            + " variables.matches_dest) ? true : false)))))))"),
    K8S(
        "k8s",
        true,
        "cel.bind(variables.env, resource.labels.?environment.orValue(\"prod\"),"
            + " cel.bind(variables.break_glass, resource.labels.?break_glass.orValue(\"false\") =="
            + " \"true\", !(variables.break_glass || resource.containers.all(c,"
            + " c.startsWith(variables.env + \".\"))) ? optional.of(\"only \" + variables.env + \""
            + " containers are allowed in namespace \" + resource.namespace) :"
            + " optional.none()))"),
    PB(
        "pb",
        true,
        "(spec.single_int32 > 10) ? optional.of(\"invalid spec, got single_int32=\" +"
            + " string(spec.single_int32) + \", wanted <= 10\") : optional.none()"),
    LIMITS(
        "limits",
        true,
        "cel.bind(variables.greeting, \"hello\", cel.bind(variables.farewell, \"goodbye\","
            + " cel.bind(variables.person, \"me\", cel.bind(variables.message_fmt, \"%s, %s\","
            + " (now.getHours() >= 20) ? cel.bind(variables.message, variables.farewell + \", \" +"
            + " variables.person, (now.getHours() < 21) ? optional.of(variables.message + \"!\") :"
            + " ((now.getHours() < 22) ? optional.of(variables.message + \"!!\") : ((now.getHours()"
            + " < 24) ? optional.of(variables.message + \"!!!\") : optional.none()))) :"
            + " optional.of(variables.greeting + \", \" + variables.person)))))");

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
      return readFromYaml(String.format("%s/policy.yaml", name));
    }

    String readConfigYamlContent() throws IOException {
      return readFromYaml(String.format("%s/config.yaml", name));
    }

    PolicyTestSuite readTestYamlContent() throws IOException {
      Yaml yaml = new Yaml(new Constructor(PolicyTestSuite.class, new LoaderOptions()));
      String testContent = readFile(String.format("%s/tests.yaml", name));

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
