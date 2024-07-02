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

import com.google.common.collect.ImmutableSet;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.policy.CelPolicyConfig.ExtensionConfig;
import dev.cel.policy.CelPolicyConfig.FunctionDecl;
import dev.cel.policy.CelPolicyConfig.OverloadDecl;
import dev.cel.policy.CelPolicyConfig.TypeDecl;
import dev.cel.policy.CelPolicyConfig.VariableDecl;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyYamlConfigParserTest {

  private static final CelPolicyConfigParser POLICY_CONFIG_PARSER =
      CelPolicyYamlConfigParser.newInstance();

  @Test
  public void config_setBasicProperties() throws Exception {
    String yamlConfig = "name: hello\n" + "description: empty\n" + "container: pb.pkg\n";

    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(yamlConfig);

    assertThat(policyConfig)
        .isEqualTo(
            CelPolicyConfig.newBuilder()
                .setConfigSource(policyConfig.configSource())
                .setName("hello")
                .setDescription("empty")
                .setContainer("pb.pkg")
                .build());
  }

  @Test
  public void config_setExtensions() throws Exception {
    String yamlConfig =
        "extensions:\n"
            + "  - name: 'bindings'\n"
            + "  - name: 'encoders'\n"
            + "  - name: 'math'\n"
            + "  - name: 'optional'\n"
            + "  - name: 'protos'\n"
            + "  - name: 'sets'\n"
            + "  - name: 'strings'\n"
            + "    version: 1";

    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(yamlConfig);

    assertThat(policyConfig)
        .isEqualTo(
            CelPolicyConfig.newBuilder()
                .setConfigSource(policyConfig.configSource())
                .setExtensions(
                    ImmutableSet.of(
                        ExtensionConfig.of("bindings"),
                        ExtensionConfig.of("encoders"),
                        ExtensionConfig.of("math"),
                        ExtensionConfig.of("optional"),
                        ExtensionConfig.of("protos"),
                        ExtensionConfig.of("sets"),
                        ExtensionConfig.of("strings", 1)))
                .build());
  }

  @Test
  public void config_setFunctions() throws Exception {
    String yamlConfig =
        "functions:\n"
            + "  - name: 'coalesce'\n"
            + "    overloads:\n"
            + "      - id: 'null_coalesce_int'\n"
            + "        target:\n"
            + "          type_name: 'null_type'\n"
            + "        args:\n"
            + "          - type_name: 'int'\n"
            + "        return:\n"
            + "          type_name: 'int'\n"
            + "      - id: 'coalesce_null_int'\n"
            + "        args:\n"
            + "          - type_name: 'null_type'\n"
            + "          - type_name: 'int'\n"
            + "        return:\n"
            + "          type_name: 'int'          \n"
            + "      - id: 'int_coalesce_int'\n"
            + "        target: \n"
            + "          type_name: 'int'\n"
            + "        args:\n"
            + "          - type_name: 'int'\n"
            + "        return: \n"
            + "          type_name: 'int'\n"
            + "      - id: 'optional_T_coalesce_T'\n"
            + "        target: \n"
            + "          type_name: 'optional_type'\n"
            + "          params:\n"
            + "            - type_name: 'T'\n"
            + "              is_type_param: true\n"
            + "        args:\n"
            + "          - type_name: 'T'\n"
            + "            is_type_param: true\n"
            + "        return: \n"
            + "          type_name: 'T'\n"
            + "          is_type_param: true";

    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(yamlConfig);

    assertThat(policyConfig)
        .isEqualTo(
            CelPolicyConfig.newBuilder()
                .setConfigSource(policyConfig.configSource())
                .setFunctions(
                    ImmutableSet.of(
                        FunctionDecl.create(
                            "coalesce",
                            ImmutableSet.of(
                                OverloadDecl.newBuilder()
                                    .setId("null_coalesce_int")
                                    .setTarget(TypeDecl.create("null_type"))
                                    .addArguments(TypeDecl.create("int"))
                                    .setReturnType(TypeDecl.create("int"))
                                    .build(),
                                OverloadDecl.newBuilder()
                                    .setId("coalesce_null_int")
                                    .addArguments(
                                        TypeDecl.create("null_type"), TypeDecl.create("int"))
                                    .setReturnType(TypeDecl.create("int"))
                                    .build(),
                                OverloadDecl.newBuilder()
                                    .setId("int_coalesce_int")
                                    .setTarget(TypeDecl.create("int"))
                                    .addArguments(TypeDecl.create("int"))
                                    .setReturnType(TypeDecl.create("int"))
                                    .build(),
                                OverloadDecl.newBuilder()
                                    .setId("optional_T_coalesce_T")
                                    .setTarget(
                                        TypeDecl.newBuilder()
                                            .setName("optional_type")
                                            .addParams(
                                                TypeDecl.newBuilder()
                                                    .setName("T")
                                                    .setIsTypeParam(true)
                                                    .build())
                                            .build())
                                    .addArguments(
                                        TypeDecl.newBuilder()
                                            .setName("T")
                                            .setIsTypeParam(true)
                                            .build())
                                    .setReturnType(
                                        TypeDecl.newBuilder()
                                            .setName("T")
                                            .setIsTypeParam(true)
                                            .build())
                                    .build()))))
                .build());
  }

  @Test
  public void config_setListVariable() throws Exception {
    String yamlConfig =
        "variables:\n"
            + "- name: 'request'\n"
            + "  type:\n"
            + "    type_name: 'list'\n"
            + "    params:\n"
            + "      - type_name: 'string'";

    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(yamlConfig);

    assertThat(policyConfig)
        .isEqualTo(
            CelPolicyConfig.newBuilder()
                .setConfigSource(policyConfig.configSource())
                .setVariables(
                    ImmutableSet.of(
                        VariableDecl.create(
                            "request",
                            TypeDecl.newBuilder()
                                .setName("list")
                                .addParams(TypeDecl.create("string"))
                                .build())))
                .build());
  }

  @Test
  public void config_setMapVariable() throws Exception {
    String yamlConfig =
        "variables:\n"
            + "- name: 'request'\n"
            + "  type:\n"
            + "    type_name: 'map'\n"
            + "    params:\n"
            + "      - type_name: 'string'\n"
            + "      - type_name: 'dyn'";

    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(yamlConfig);

    assertThat(policyConfig)
        .isEqualTo(
            CelPolicyConfig.newBuilder()
                .setConfigSource(policyConfig.configSource())
                .setVariables(
                    ImmutableSet.of(
                        VariableDecl.create(
                            "request",
                            TypeDecl.newBuilder()
                                .setName("map")
                                .addParams(TypeDecl.create("string"), TypeDecl.create("dyn"))
                                .build())))
                .build());
  }

  @Test
  public void config_setMessageVariable() throws Exception {
    String yamlConfig =
        "variables:\n"
            + "- name: 'request'\n"
            + "  type:\n"
            + "    type_name: 'google.rpc.context.AttributeContext.Request'";

    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(yamlConfig);

    assertThat(policyConfig)
        .isEqualTo(
            CelPolicyConfig.newBuilder()
                .setConfigSource(policyConfig.configSource())
                .setVariables(
                    ImmutableSet.of(
                        VariableDecl.create(
                            "request",
                            TypeDecl.create("google.rpc.context.AttributeContext.Request"))))
                .build());
  }

  @Test
  public void config_parseErrors(@TestParameter ConfigParseErrorTestcase testCase) {
    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> POLICY_CONFIG_PARSER.parse(testCase.yamlConfig));
    assertThat(e).hasMessageThat().isEqualTo(testCase.expectedErrorMessage);
  }

  // Note: dangling comments in expressions below is to retain the newlines by preventing auto
  // formatter from compressing them in a single line.
  private enum ConfigParseErrorTestcase {
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
    ILLEGAL_YAML_TYPE_CONFIG_KEY(
        "1: test",
        "ERROR: <input>:1:1: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " | 1: test\n"
            + " | ^"),
    ILLEGAL_YAML_TYPE_CONFIG_VALUE(
        "test: 1", "ERROR: <input>:1:1: Unknown config tag: test\n" + " | test: 1\n" + " | ^"),
    ILLEGAL_YAML_TYPE_VARIABLE_LIST(
        "variables: 1",
        "ERROR: <input>:1:12: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " | variables: 1\n"
            + " | ...........^"),
    ILLEGAL_YAML_TYPE_VARIABLE_VALUE(
        "variables:\n - 1",
        "ERROR: <input>:2:4: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |  - 1\n"
            + " | ...^"),
    ILLEGAL_YAML_TYPE_FUNCTION_LIST(
        "functions: 1",
        "ERROR: <input>:1:12: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " | functions: 1\n"
            + " | ...........^"),
    ILLEGAL_YAML_TYPE_FUNCTION_VALUE(
        "functions:\n - 1",
        "ERROR: <input>:2:4: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |  - 1\n"
            + " | ...^"),
    ILLEGAL_YAML_TYPE_OVERLOAD_LIST(
        "functions:\n" //
            + " - name: foo\n" //
            + "   overloads: 1",
        "ERROR: <input>:3:15: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " |    overloads: 1\n"
            + " | ..............^"),
    ILLEGAL_YAML_TYPE_OVERLOAD_VALUE(
        "functions:\n" //
            + " - name: foo\n" //
            + "   overloads:\n" //
            + "    - 2",
        "ERROR: <input>:4:7: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |     - 2\n"
            + " | ......^"),
    ILLEGAL_YAML_TYPE_OVERLOAD_VALUE_MAP_KEY(
        "functions:\n" //
            + " - name: foo\n" //
            + "   overloads:\n" //
            + "      - 2: test",
        "ERROR: <input>:4:9: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " |       - 2: test\n"
            + " | ........^\n"
            + "ERROR: <input>:4:9: Missing required attribute(s): id, return\n"
            + " |       - 2: test\n"
            + " | ........^"),
    ILLEGAL_YAML_TYPE_EXTENSION_LIST(
        "extensions: 1",
        "ERROR: <input>:1:13: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " | extensions: 1\n"
            + " | ............^"),
    ILLEGAL_YAML_TYPE_EXTENSION_VALUE(
        "extensions:\n - 1",
        "ERROR: <input>:2:4: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |  - 1\n"
            + " | ...^"),
    ILLEGAL_YAML_TYPE_TYPE_DECL(
        "variables:\n" //
            + " - name: foo\n" //
            + "   type: 1",
        "ERROR: <input>:3:10: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:map]\n"
            + " |    type: 1\n"
            + " | .........^"),
    ILLEGAL_YAML_TYPE_TYPE_VALUE(
        "variables:\n"
            + " - name: foo\n"
            + "   type:\n"
            + "     type_name: bar\n"
            + "     1: hello",
        "ERROR: <input>:5:6: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " |      1: hello\n"
            + " | .....^"),
    ILLEGAL_YAML_TYPE_TYPE_PARAMS_LIST(
        "variables:\n"
            + " - name: foo\n"
            + "   type:\n"
            + "     type_name: bar\n"
            + "     params: 1",
        "ERROR: <input>:4:6: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " |      type_name: bar\n"
            + " | .....^"),
    UNSUPPORTED_CONFIG_TAG(
        "unsupported: test",
        "ERROR: <input>:1:1: Unknown config tag: unsupported\n"
            + " | unsupported: test\n"
            + " | ^"),
    UNSUPPORTED_EXTENSION_TAG(
        "extensions:\n" //
            + "  - name: foo\n" //
            + "    unsupported: test",
        "ERROR: <input>:3:5: Unsupported extension tag: unsupported\n"
            + " |     unsupported: test\n"
            + " | ....^"),
    UNSUPPORTED_TYPE_DECL_TAG(
        "variables:\n"
            + "- name: foo\n"
            + "  type:\n"
            + "     type_name: bar\n"
            + "     unsupported: hello",
        "ERROR: <input>:5:6: Unsupported type decl tag: unsupported\n"
            + " |      unsupported: hello\n"
            + " | .....^"),
    MISSING_VARIABLE_PROPERTIES(
        "variables:\n - illegal: 2",
        "ERROR: <input>:2:4: Unsupported variable tag: illegal\n"
            + " |  - illegal: 2\n"
            + " | ...^\n"
            + "ERROR: <input>:2:4: Missing required attribute(s): name, type\n"
            + " |  - illegal: 2\n"
            + " | ...^"),
    MISSING_OVERLOAD_RETURN(
        "functions:\n"
            + "  - name: 'missing_return'\n"
            + "    overloads:\n"
            + "      - id: 'zero_arity'\n",
        "ERROR: <input>:4:9: Missing required attribute(s): return\n"
            + " |       - id: 'zero_arity'\n"
            + " | ........^"),
    MISSING_FUNCTION_NAME(
        "functions:\n"
            + "  - overloads:\n"
            + "      - id: 'foo'\n"
            + "        return:\n"
            + "          type_name: 'string'\n",
        "ERROR: <input>:2:5: Missing required attribute(s): name\n"
            + " |   - overloads:\n"
            + " | ....^"),
    MISSING_OVERLOAD(
        "functions:\n" + "  - name: 'missing_overload'\n",
        "ERROR: <input>:2:5: Missing required attribute(s): overloads\n"
            + " |   - name: 'missing_overload'\n"
            + " | ....^"),
    MISSING_EXTENSION_NAME(
        "extensions:\n" + "- version: 0",
        "ERROR: <input>:2:3: Missing required attribute(s): name\n"
            + " | - version: 0\n"
            + " | ..^"),
    ;

    private final String yamlConfig;
    private final String expectedErrorMessage;

    ConfigParseErrorTestcase(String yamlConfig, String expectedErrorMessage) {
      this.yamlConfig = yamlConfig;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }
}
