// Copyright 2025 Google LLC
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
package dev.cel.testing.testrunner;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Input.Binding;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelTestSuiteYamlParserTest {

  private static final CelTestSuiteYamlParser CEL_TEST_SUITE_YAML_PARSER =
      CelTestSuiteYamlParser.newInstance();

  @Test
  public void parseTestSuite_withBindingsAsInput_success() throws CelTestSuiteException {
    String testSuiteYamlContent =
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "         value:\n"
            + "           - nested_key: true\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n"
            + "  - name: 'test_case_name_2'\n"
            + "    description: 'test_case_description_2'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "         value: 1\n"
            + "    output:\n"
            + "      value: 2.20\n";

    CelTestSuite testSuite = CEL_TEST_SUITE_YAML_PARSER.parse(testSuiteYamlContent);
    CelTestSuite expectedTestSuite =
        CelTestSuite.newBuilder()
            .setSource(testSuite.source().get())
            .setName("test_suite_name")
            .setDescription("test_suite_description")
            .setSections(
                ImmutableSet.of(
                    CelTestSuite.CelTestSection.newBuilder()
                        .setName("test_section_name")
                        .setDescription("test_section_description")
                        .setTests(
                            ImmutableSet.of(
                                CelTestSuite.CelTestSection.CelTestCase.newBuilder()
                                    .setName("test_case_name")
                                    .setDescription("test_case_description")
                                    .setInput(
                                        CelTestSuite.CelTestSection.CelTestCase.Input.ofBindings(
                                            ImmutableMap.of(
                                                "test_key",
                                                Binding.ofValue(
                                                    ImmutableList.of(
                                                        ImmutableMap.of("nested_key", true))))))
                                    .setOutput(
                                        CelTestSuite.CelTestSection.CelTestCase.Output
                                            .ofResultValue("test_result_value"))
                                    .build(),
                                CelTestSuite.CelTestSection.CelTestCase.newBuilder()
                                    .setName("test_case_name_2")
                                    .setDescription("test_case_description_2")
                                    .setInput(
                                        CelTestSuite.CelTestSection.CelTestCase.Input.ofBindings(
                                            ImmutableMap.of("test_key", Binding.ofValue(1))))
                                    .setOutput(
                                        CelTestSuite.CelTestSection.CelTestCase.Output
                                            .ofResultValue(2.20))
                                    .build()))
                        .build()))
            .build();

    assertThat(testSuite).isEqualTo(expectedTestSuite);
    assertThat(testSuite.source().get().getPositionsMap()).isNotEmpty();
  }

  @Test
  public void parseTestSuite_withExprAsOutput_success() throws CelTestSuiteException {
    String testSuiteYamlContent =
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "         expr: 'some_value'\n"
            + "    output:\n"
            + "      expr: '1 == 1'\n";

    CelTestSuite testSuite = CEL_TEST_SUITE_YAML_PARSER.parse(testSuiteYamlContent);
    CelTestSuite expectedTestSuite =
        CelTestSuite.newBuilder()
            .setSource(testSuite.source().get())
            .setName("test_suite_name")
            .setDescription("test_suite_description")
            .setSections(
                ImmutableSet.of(
                    CelTestSuite.CelTestSection.newBuilder()
                        .setName("test_section_name")
                        .setDescription("test_section_description")
                        .setTests(
                            ImmutableSet.of(
                                CelTestSuite.CelTestSection.CelTestCase.newBuilder()
                                    .setName("test_case_name")
                                    .setDescription("test_case_description")
                                    .setInput(
                                        CelTestSuite.CelTestSection.CelTestCase.Input.ofBindings(
                                            ImmutableMap.of(
                                                "test_key", Binding.ofExpr("some_value"))))
                                    .setOutput(
                                        CelTestSuite.CelTestSection.CelTestCase.Output.ofResultExpr(
                                            "1 == 1"))
                                    .build()))
                        .build()))
            .build();

    assertThat(testSuite).isEqualTo(expectedTestSuite);
    assertThat(testSuite.source().get().getPositionsMap()).isNotEmpty();
  }

  @Test
  public void parseTestSuite_failure_throwsException(
      @TestParameter TestSuiteYamlParsingErrorTestCase testCase) throws CelTestSuiteException {
    CelTestSuiteException celTestSuiteException =
        assertThrows(
            CelTestSuiteException.class,
            () -> CEL_TEST_SUITE_YAML_PARSER.parse(testCase.testSuiteYamlContent));

    assertThat(celTestSuiteException).hasMessageThat().contains(testCase.expectedErrorMessage);
  }

  private enum TestSuiteYamlParsingErrorTestCase {
    TEST_SUITE_WITH_MISALIGNED_NAME_TAG(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "      test_key: 'test_value'\n"
            + "    value: 'test_result_value'\n",
        "YAML document is malformed: while parsing a block mapping\n"
            + " in 'reader', line 1, column 1:\n"
            + "    name: 'test_suite_name'\n"
            + "    ^"),
    TEST_SUITE_WITH_ILLEGAL_TEST_SUITE_TAG(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n"
            + "unknown_tag: 'test_value'\n",
        "ERROR: <input>:14:1: Unknown test suite tag: unknown_tag\n"
            + " | unknown_tag: 'test_value'\n"
            + " | ^"),
    TEST_SUITE_WITH_ILLEGAL_TEST_SECTION_TAG(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  unknown_tag: 'test_value'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:5:3: Unknown test section tag: unknown_tag\n"
            + " |   unknown_tag: 'test_value'\n"
            + " | ..^"),
    TEST_SUITE_WITH_ILLEGAL_TEST_CASE_OUTPUT_TAG(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      unknown_tag: 'test_result_value'\n",
        "ERROR: <input>:13:7: Unknown output tag: unknown_tag\n"
            + " |       unknown_tag: 'test_result_value'\n"
            + " | ......^"),
    TEST_SUITE_WITH_ILLEGAL_TEST_CASE_TAG(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n"
            + "    unknown_tag: 'test_value'\n",
        "ERROR: <input>:14:5: Unknown test case tag: unknown_tag\n"
            + " |     unknown_tag: 'test_value'\n"
            + " | ....^"),
    ILLEGAL_TEST_SUITE_WITH_SECTION_NOT_LIST(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "  name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "        test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:4:3: Got yaml node type tag:yaml.org,2002:map, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " |   name: 'test_section_name'\n"
            + " | ..^\n"
            + "ERROR: <input>:4:3: Sections is not a list: tag:yaml.org,2002:map\n"
            + " |   name: 'test_section_name'\n"
            + " | ..^"),
    ILLEGAL_TEST_SUITE_WITH_TEST_SUITE_NOT_MAP(
        "- name: 'test_suite_name'\n"
            + "- description: 'test_suite_description'\n"
            + "- sections:\n"
            + "  name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "        test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:1:1: Got yaml node type tag:yaml.org,2002:seq, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " | - name: 'test_suite_name'\n"
            + " | ^\n"
            + "ERROR: <input>:1:1: Unknown test suite type: tag:yaml.org,2002:seq\n"
            + " | - name: 'test_suite_name'\n"
            + " | ^"),
    ILLEGAL_TEST_SUITE_WITH_OUTPUT_NOT_MAP(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      - value: 'test_result_value'\n",
        "ERROR: <input>:13:7: Got yaml node type tag:yaml.org,2002:seq, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |       - value: 'test_result_value'\n"
            + " | ......^\n"
            + "ERROR: <input>:13:7: Output is not a map: tag:yaml.org,2002:seq\n"
            + " |       - value: 'test_result_value'\n"
            + " | ......^"),
    ILLEGAL_TEST_SUITE_WITH_MORE_THAN_ONE_INPUT_VALUES_AGAINST_KEY(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          value: 'test_value'\n"
            + "          value: 'test_value_2'\n"
            + "          expr: 'test_expr'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:11:11: Input binding node must have exactly one value:"
            + " tag:yaml.org,2002:map\n"
            + " |           value: 'test_value'\n"
            + " | ..........^"),
    ILLEGAL_TEST_SUITE_WITH_UNKNOWN_INPUT_BINDING_VALUE_TAG(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          something: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:11:11: Unknown input binding value tag: something\n"
            + " |           something: 'test_value'\n"
            + " | ..........^"),
    ILLEGAL_TEST_SUITE_WITH_BINDINGS_NOT_MAP(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "         - test_key:\n"
            + "           value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:10:10: Got yaml node type tag:yaml.org,2002:seq, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |          - test_key:\n"
            + " | .........^\n"
            + "ERROR: <input>:10:10: Input is not a map: tag:yaml.org,2002:seq\n"
            + " |          - test_key:\n"
            + " | .........^"),
    ILLEGAL_TEST_SUITE_WITH_ILLEGAL_BINDINGS_VALUE(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "         test_key:\n"
            + "            - value\n"
            + "             - 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:11:13: Got yaml node type tag:yaml.org,2002:seq, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |             - value\n"
            + " | ............^\n"
            + "ERROR: <input>:11:13: Input binding node is not a map: tag:yaml.org,2002:seq\n"
            + " |             - value\n"
            + " | ............^"),
    ILLEGAL_TEST_SUITE_WITH_ILLEGAL_CONTEXT_EXPR_VALUE(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    context_expr:\n"
            + "        test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:10:9: Got yaml node type tag:yaml.org,2002:map, wanted type(s)"
            + " [tag:yaml.org,2002:str]\n"
            + " |         test_key:\n"
            + " | ........^\n"
            + "ERROR: <input>:10:9: Input context is not a string: tag:yaml.org,2002:map\n"
            + " |         test_key:\n"
            + " | ........^"),
    ILLEGAL_TEST_SUITE_WITH_TESTS_NOT_LIST(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "sections:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "    name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "        test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:7:5: Got yaml node type tag:yaml.org,2002:map, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " |     name: 'test_case_name'\n"
            + " | ....^\n"
            + "ERROR: <input>:7:5: Tests is not a list: tag:yaml.org,2002:map\n"
            + " |     name: 'test_case_name'\n"
            + " | ....^"),
    TEST_SUITE_WITH_ILLEGAL_TEST_SUITE_FORMAT(
        "- name: 'test_suite_name'\n"
            + "- name: 'test_section_name'\n"
            + "- name: 'test_section_name_2'\n",
        "ERROR: <input>:1:1: Unknown test suite type: tag:yaml.org,2002:seq\n"
            + " | - name: 'test_suite_name'\n"
            + " | ^"),
    TEST_SUITE_WITH_ILLEGAL_SECTION_TYPE(
        "name: 'test_suite_name'\n"
            + "description: 'test_suite_description'\n"
            + "1:\n"
            + "- name: 'test_section_name'\n"
            + "  description: 'test_section_description'\n"
            + "  tests:\n"
            + "  - name: 'test_case_name'\n"
            + "    description: 'test_case_description'\n"
            + "    input:\n"
            + "       test_key:\n"
            + "          value: 'test_value'\n"
            + "    output:\n"
            + "      value: 'test_result_value'\n",
        "ERROR: <input>:3:1: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " | 1:\n"
            + " | ^"),
    ;

    private final String testSuiteYamlContent;
    private final String expectedErrorMessage;

    TestSuiteYamlParsingErrorTestCase(String testSuiteYamlContent, String expectedErrorMessage) {
      this.testSuiteYamlContent = testSuiteYamlContent;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }
}
