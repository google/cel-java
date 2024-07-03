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
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

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
            + " variables.matches_dest) ? true : false)))))))");

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

  private PolicyTestHelper() {}
}
