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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Iterables;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.formats.ValueString;
import dev.cel.policy.PolicyTestHelper.K8sTagHandler;
import dev.cel.policy.PolicyTestHelper.TestYamlPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyYamlParserTest {

  private static final CelPolicyParser POLICY_PARSER =
      CelPolicyParserFactory.newYamlParserBuilder().addTagVisitor(new K8sTagHandler()).build();

  @Test
  public void parseYamlPolicy_success(@TestParameter TestYamlPolicy yamlPolicy) throws Exception {
    String policySource = yamlPolicy.readPolicyYamlContent();
    String description = yamlPolicy.getPolicyName();

    CelPolicy policy = POLICY_PARSER.parse(policySource, description);

    assertThat(policy.name().value()).isEqualTo(yamlPolicy.getPolicyName());
    assertThat(policy.policySource().getContent().toString()).isEqualTo(policySource);
    assertThat(policy.policySource().getDescription()).isEqualTo(description);
  }

  @Test
  public void parser_setEmpty() throws Exception {
    assertThrows(CelPolicyValidationException.class, () -> POLICY_PARSER.parse("", ""));
  }

  @Test
  public void parseYamlPolicy_withDescription() throws Exception {
    String policySource =
        "rule:\n"
            + "  variables:\n"
            + "  - name: 'variable_with_description'\n"
            + "    description: 'this is a description of the variable'\n"
            + "    expression: 'true'";

    CelPolicy policy = POLICY_PARSER.parse(policySource);

    assertThat(policy.rule().variables()).hasSize(1);
    assertThat(Iterables.getOnlyElement(policy.rule().variables()).description())
        .hasValue(ValueString.of(10, "this is a description of the variable"));
  }

  @Test
  public void parseYamlPolicy_withDisplayName() throws Exception {
    String policySource =
        "rule:\n"
            + "  variables:\n"
            + "  - name: 'variable_with_description'\n"
            + "    display_name: 'Display Name'\n"
            + "    expression: 'true'";

    CelPolicy policy = POLICY_PARSER.parse(policySource);

    assertThat(policy.rule().variables()).hasSize(1);
    assertThat(Iterables.getOnlyElement(policy.rule().variables()).displayName())
        .hasValue(ValueString.of(10, "Display Name"));
  }

  @Test
  public void parseYamlPolicy_withExplanation() throws Exception {
    String policySource =
        "rule:\n"
            + "  match:\n"
            + "  - output: 'true'\n"
            + "    explanation: \"'custom explanation'\"";

    CelPolicy policy = POLICY_PARSER.parse(policySource);

    assertThat(policy.rule().matches()).hasSize(1);
    assertThat(Iterables.getOnlyElement(policy.rule().matches()).explanation())
        .hasValue(ValueString.of(11, "'custom explanation'"));
  }

  @Test
  public void parseYamlPolicy_errors(@TestParameter PolicyParseErrorTestCase testCase) {
    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class, () -> POLICY_PARSER.parse(testCase.yamlPolicy));
    assertThat(e).hasMessageThat().isEqualTo(testCase.expectedErrorMessage);
  }

  private enum PolicyParseErrorTestCase {
    MALFORMED_YAML_DOCUMENT(
        "a:\na",
        "YAML document is malformed: while scanning a simple key\n"
            + " in 'reader', line 2, column 1:\n"
            + "    a\n"
            + "    ^\n"
            + "could not find expected ':'\n"
            + " in 'reader', line 2, column 2:\n"
            + "    a\n"
            + "     ^\n"),
    ILLEGAL_YAML_TYPE_POLICY_KEY(
        "1: test",
        "ERROR: <input>:1:1: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " | 1: test\n"
            + " | ^"),
    ILLEGAL_YAML_TYPE_ON_NAME_VALUE(
        "name: \n" + "  illegal: yaml-type",
        "ERROR: <input>:2:3: Got yaml node type tag:yaml.org,2002:map, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " |   illegal: yaml-type\n"
            + " | ..^"),
    ILLEGAL_YAML_TYPE_ON_RULE_VALUE(
        "rule: illegal",
        "ERROR: <input>:1:7: Got yaml node type tag:yaml.org,2002:str, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " | rule: illegal\n"
            + " | ......^"),
    ILLEGAL_YAML_TYPE_ON_RULE_MAP_KEY(
        "rule: \n" + "  1: foo",
        "ERROR: <input>:2:3: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " |   1: foo\n"
            + " | ..^"),
    ILLEGAL_YAML_TYPE_ON_MATCHES_VALUE(
        "rule:\n" + "  match: illegal\n",
        "ERROR: <input>:2:10: Got yaml node type tag:yaml.org,2002:str, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " |   match: illegal\n"
            + " | .........^"),
    ILLEGAL_YAML_TYPE_ON_MATCHES_LIST(
        "rule:\n" + "  match:\n" + "    - illegal",
        "ERROR: <input>:3:7: Got yaml node type tag:yaml.org,2002:str, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |     - illegal\n"
            + " | ......^"),
    ILLEGAL_YAML_TYPE_ON_MATCH_MAP_KEY(
        "rule:\n" + "  match:\n" + "    - 1 : foo\n" + "      output: 'hi'",
        "ERROR: <input>:3:7: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " |     - 1 : foo\n"
            + " | ......^"),
    ILLEGAL_YAML_TYPE_ON_VARIABLE_VALUE(
        "rule:\n" + "  variables: illegal\n",
        "ERROR: <input>:2:14: Got yaml node type tag:yaml.org,2002:str, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " |   variables: illegal\n"
            + " | .............^"),
    ILLEGAL_YAML_TYPE_ON_VARIABLE_MAP_KEY(
        "rule:\n" + "  variables:\n" + "    - illegal",
        "ERROR: <input>:3:7: Got yaml node type tag:yaml.org,2002:str, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |     - illegal\n"
            + " | ......^"),
    MULTIPLE_YAML_DOCS(
        "name: foo\n" + "---\n" + "name: bar",
        "YAML document is malformed: expected a single document in the stream\n"
            + " in 'reader', line 1, column 1:\n"
            + "    name: foo\n"
            + "    ^\n"
            + "but found another document\n"
            + " in 'reader', line 2, column 1:\n"
            + "    ---\n"
            + "    ^\n"),
    UNSUPPORTED_RULE_TAG(
        "rule:\n" + "  custom: yaml-type",
        "ERROR: <input>:2:3: Unsupported rule tag: custom\n"
            + " |   custom: yaml-type\n"
            + " | ..^"),
    UNSUPPORTED_POLICY_TAG(
        "inputs:\n" + "  - name: a\n" + "  - name: b",
        "ERROR: <input>:1:1: Unsupported policy tag: inputs\n" + " | inputs:\n" + " | ^"),
    UNSUPPORTED_VARIABLE_TAG(
        "rule:\n" //
            + "  variables:\n" //
            + "    - name: 'hello'\n" //
            + "      expression: 'true'\n" //
            + "      alt_name: 'bool_true'",
        "ERROR: <input>:5:7: Unsupported variable tag: alt_name\n"
            + " |       alt_name: 'bool_true'\n"
            + " | ......^"),
    MISSING_VARIABLE_NAME(
        "rule:\n" //
            + "  variables:\n" //
            + "    - expression: 'true'", //
        "ERROR: <input>:3:7: Missing required attribute(s): name\n"
            + " |     - expression: 'true'\n"
            + " | ......^"),
    MISSING_VARIABLE_EXPRESSION(
        "rule:\n" //
            + "  variables:\n" //
            + "    - name: 'hello'", //
        "ERROR: <input>:3:7: Missing required attribute(s): expression\n"
            + " |     - name: 'hello'\n"
            + " | ......^"),
    UNSUPPORTED_MATCH_TAG(
        "rule:\n"
            + "  match:\n"
            + "    - name: 'true'\n"
            + "      output: 'hi'\n"
            + "      alt_name: 'bool_true'",
        "ERROR: <input>:3:7: Unsupported match tag: name\n"
            + " |     - name: 'true'\n"
            + " | ......^\n"
            + "ERROR: <input>:5:7: Unsupported match tag: alt_name\n"
            + " |       alt_name: 'bool_true'\n"
            + " | ......^"),
    MATCH_MISSING_OUTPUT_AND_RULE(
        "rule:\n" //
            + "  match:\n" //
            + "    - condition: 'true'",
        "ERROR: <input>:3:7: Missing required attribute(s): output or a rule\n"
            + " |     - condition: 'true'\n"
            + " | ......^"),
    MATCH_OUTPUT_SET_THEN_RULE(
        "rule:\n"
            + "  match:\n"
            + "    - condition: \"true\"\n"
            + "      output: \"world\"\n"
            + "      rule:\n"
            + "        match:\n"
            + "          - output: \"hello\"",
        "ERROR: <input>:5:7: Only the rule or the output may be set\n"
            + " |       rule:\n"
            + " | ......^"),
    MATCH_RULE_SET_THEN_OUTPUT(
        "rule:\n"
            + "  match:\n"
            + "    - condition: \"true\"\n"
            + "      rule:\n"
            + "        match:\n"
            + "          - output: \"hello\"\n"
            + "      output: \"world\"",
        "ERROR: <input>:7:7: Only the rule or the output may be set\n"
            + " |       output: \"world\"\n"
            + " | ......^"),
    MATCH_NESTED_RULE_SET_THEN_EXPLANATION(
        "rule:\n"
            + "  match:\n"
            + "    - condition: \"true\"\n"
            + "      rule:\n"
            + "        match:\n"
            + "          - output: \"hello\"\n"
            + "      explanation: \"foo\"",
        "ERROR: <input>:7:7: Explanation can only be set on output match cases, not nested rules\n"
            + " |       explanation: \"foo\"\n"
            + " | ......^"),
    MATCH_EXPLANATION_SET_THEN_NESTED_RULE(
        "rule:\n"
            + "  match:\n"
            + "    - condition: \"true\"\n"
            + "      explanation: \"foo\"\n"
            + "      rule:\n"
            + "        match:\n"
            + "          - output: \"hello\"\n",
        "ERROR: <input>:4:21: Explanation can only be set on output match cases, not nested rules\n"
            + " |       explanation: \"foo\"\n"
            + " | ....................^"),
    INVALID_ROOT_NODE_TYPE(
        "- rule:\n" + "    id: a",
        "ERROR: <input>:1:1: Got yaml node type tag:yaml.org,2002:seq, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " | - rule:\n"
            + " | ^"),
    ILLEGAL_RULE_DESCRIPTION_TYPE(
        "rule:\n" + "  description: 1",
        "ERROR: <input>:2:16: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " |   description: 1\n"
            + " | ...............^"),
    ;

    private final String yamlPolicy;
    private final String expectedErrorMessage;

    PolicyParseErrorTestCase(String yamlPolicy, String expectedErrorMessage) {
      this.yamlPolicy = yamlPolicy;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }
}
