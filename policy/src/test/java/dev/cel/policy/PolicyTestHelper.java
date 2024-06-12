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
/**
 * Package-private class to assist with policy testing.
 */
final class PolicyTestHelper {

  enum YamlPolicy {
    NESTED_RULE("nested_rule", true),
    REQUIRED_LABELS("required_labels", true),
    RESTRICTED_DESTINATIONS("restricted_destinations", false);

    private final String name;
    private final boolean producesOptionalResult;

    YamlPolicy(String name, boolean producesOptionalResult) {
      this.name = name;
      this.producesOptionalResult = producesOptionalResult;
    }

    String getPolicyName() {
      return name;
    }

    boolean producesOptionalResult() {
      return this.producesOptionalResult;
    }

    CelPolicySource readPolicyYamlContent() throws IOException {
      String policyContent = readFile(String.format("%s/policy.yaml", name));
      return CelPolicySource.newBuilder(policyContent).build();
    }

    CelPolicySource readConfigYamlContent() throws IOException {
      String configContent = readFile(String.format("%s/config.yaml", name));
      return CelPolicySource.newBuilder(configContent).build();
    }

    PolicyTestSuite readTestYamlContent() throws IOException {
      Yaml yaml = new Yaml(new Constructor(PolicyTestSuite.class, new LoaderOptions()));
      String testContent = readFile(String.format("%s/tests.yaml", name));

      return yaml.load(testContent);
    }
  }

  /**
   * TestSuite describes a set of tests divided by section.
   *
   * <p>Visibility must be public for YAML deserialization to work. This is effectively package-private
   * since the outer class is.
   *
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
}
