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
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Ascii;
import com.google.common.io.Resources;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyYamlParserTest {

  private static final CelPolicyParser YAML_POLICY_PARSER = CelPolicyYamlParser.newInstance();

  @Test
  public void parseYamlPolicy_success(@TestParameter PolicyTestCase policyTestcase)
      throws Exception {
    String yamlFileLocation = String.format("%s/policy.yaml", policyTestcase.name);
    String yamlContent = readFile(yamlFileLocation);
    CelPolicySource policySource = CelPolicySource.create(yamlContent, yamlContent);

    CelPolicy policy = YAML_POLICY_PARSER.parse(policySource);

    assertThat(policy.name()).isEqualTo(policy.name());
    assertThat(policy.policySource()).isEqualTo(policySource);
  }

  private enum PolicyTestCase {
    NESTED_RULE("nested_rule"),
    REQUIRED_LABELS("required_labels"),
    RESTRICTED_DESTINATIONS("restricted_destinations");

    private final String name;

    PolicyTestCase(String name) {
      this.name = name;
    }
  }

  private static String readFile(String path) throws IOException {
    return Resources.toString(Resources.getResource(Ascii.toLowerCase(path)), UTF_8);
  }
}