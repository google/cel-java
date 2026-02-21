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

package dev.cel.policy.testing;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.runtime.CelEvaluationException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Helper to assist with policy testing.
 *
 **/
public final class PolicyTestSuiteHelper {

  /**
   * TODO
   */
  public static PolicyTestSuite readTestSuite(String path) throws IOException {
    Yaml yaml = new Yaml(new Constructor(PolicyTestSuite.class, new LoaderOptions()));
    String testContent = readFile(path);

    return yaml.load(testContent);
  }

  /**
   * TODO
   * @param yamlPath
   * @return
   * @throws IOException
   */
  public static String readFromYaml(String yamlPath) throws IOException {
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

        public ImmutableMap<String, Object> toInputMap(Cel cel)
            throws CelValidationException, CelEvaluationException {
          ImmutableMap.Builder<String, Object> inputBuilder = ImmutableMap.builderWithExpectedSize(
              input.size());
          for (Map.Entry<String, PolicyTestInput> entry : input.entrySet()) {
            String exprInput = entry.getValue().getExpr();
            if (isNullOrEmpty(exprInput)) {
              inputBuilder.put(entry.getKey(), entry.getValue().getValue());
            } else {
              CelAbstractSyntaxTree exprInputAst = cel.compile(exprInput).getAst();
              inputBuilder.put(entry.getKey(), cel.createProgram(exprInputAst).eval());
            }
          }

          return inputBuilder.buildOrThrow();
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

  private PolicyTestSuiteHelper() {}
}
