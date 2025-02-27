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

package dev.cel.bundle;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.rpc.context.AttributeContext;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironment.FunctionDecl;
import dev.cel.bundle.CelEnvironment.OverloadDecl;
import dev.cel.bundle.CelEnvironment.TypeDecl;
import dev.cel.bundle.CelEnvironment.VariableDecl;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelLateFunctionBindings;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelEnvironmentYamlParserTest {

  private static final Cel CEL_WITH_MESSAGE_TYPES =
      CelFactory.standardCelBuilder()
          .addMessageTypes(AttributeContext.Request.getDescriptor())
          .build();

  private static final CelEnvironmentYamlParser ENVIRONMENT_PARSER =
      CelEnvironmentYamlParser.newInstance();

  @Test
  public void environment_setEmpty() throws Exception {
    assertThrows(CelEnvironmentException.class, () -> ENVIRONMENT_PARSER.parse(""));
  }

  @Test
  public void environment_setBasicProperties() throws Exception {
    String yamlConfig = "name: hello\n" + "description: empty\n" + "container: pb.pkg\n";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
                .setName("hello")
                .setDescription("empty")
                .setContainer("pb.pkg")
                .build());
  }

  @Test
  public void environment_setExtensions() throws Exception {
    String yamlConfig =
        "extensions:\n"
            + "  - name: 'bindings'\n"
            + "  - name: 'encoders'\n"
            + "  - name: 'lists'\n"
            + "  - name: 'math'\n"
            + "  - name: 'optional'\n"
            + "  - name: 'protos'\n"
            + "  - name: 'sets'\n"
            + "  - name: 'strings'\n"
            + "    version: 1";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
                .setExtensions(
                    ImmutableSet.of(
                        ExtensionConfig.of("bindings"),
                        ExtensionConfig.of("encoders"),
                        ExtensionConfig.of("lists"),
                        ExtensionConfig.of("math"),
                        ExtensionConfig.of("optional"),
                        ExtensionConfig.of("protos"),
                        ExtensionConfig.of("sets"),
                        ExtensionConfig.of("strings", 1)))
                .build());
    assertThat(environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void environment_setExtensionVersionToLatest() throws Exception {
    String yamlConfig =
        "extensions:\n" //
            + "  - name: 'bindings'\n" //
            + "    version: latest";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
                .setExtensions(ImmutableSet.of(ExtensionConfig.of("bindings", Integer.MAX_VALUE)))
                .build());
    assertThat(environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void environment_setExtensionVersionToInvalidValue() throws Exception {
    String yamlConfig =
        "extensions:\n" //
            + "  - name: 'bindings'\n" //
            + "    version: invalid";

    CelEnvironmentException e =
        assertThrows(CelEnvironmentException.class, () -> ENVIRONMENT_PARSER.parse(yamlConfig));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "ERROR: <input>:3:5: Unsupported version tag: version\n"
                + " |     version: invalid\n"
                + " | ....^");
  }

  @Test
  public void environment_setFunctions() throws Exception {
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

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
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
    assertThat(environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void environment_setListVariable() throws Exception {
    String yamlConfig =
        "variables:\n"
            + "- name: 'request'\n"
            + "  type_name: 'list'\n"
            + "  params:\n"
            + "    - type_name: 'string'";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
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
  public void environment_setMapVariable() throws Exception {
    String yamlConfig =
        "variables:\n"
            + "- name: 'request'\n"
            + "  type:\n"
            + "    type_name: 'map'\n"
            + "    params:\n"
            + "      - type_name: 'string'\n"
            + "      - type_name: 'dyn'";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
                .setVariables(
                    ImmutableSet.of(
                        VariableDecl.create(
                            "request",
                            TypeDecl.newBuilder()
                                .setName("map")
                                .addParams(TypeDecl.create("string"), TypeDecl.create("dyn"))
                                .build())))
                .build());
    assertThat(environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void environment_setMessageVariable() throws Exception {
    String yamlConfig =
        "variables:\n"
            + "- name: 'request'\n"
            + "  type:\n"
            + "    type_name: 'google.rpc.context.AttributeContext.Request'";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
                .setVariables(
                    ImmutableSet.of(
                        VariableDecl.create(
                            "request",
                            TypeDecl.create("google.rpc.context.AttributeContext.Request"))))
                .build());
    assertThat(environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void environment_setContainer() throws Exception {
    String yamlConfig =
        "container: google.rpc.context\n"
            + "variables:\n"
            + "- name: 'request'\n"
            + "  type:\n"
            + "    type_name: 'google.rpc.context.AttributeContext.Request'";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setContainer("google.rpc.context")
                .setSource(environment.source().get())
                .setVariables(
                    ImmutableSet.of(
                        VariableDecl.create(
                            "request",
                            TypeDecl.create("google.rpc.context.AttributeContext.Request"))))
                .build());
    assertThat(environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void environment_withInlinedVariableDecl() throws Exception {
    String yamlConfig =
        "variables:\n"
            + "- name: 'request'\n"
            + "  type_name: 'google.rpc.context.AttributeContext.Request'\n"
            + "- name: 'map_var'\n"
            + "  type_name: 'map'\n"
            + "  params:\n"
            + "    - type_name: 'string'\n"
            + "    - type_name: 'string'";

    CelEnvironment environment = ENVIRONMENT_PARSER.parse(yamlConfig);

    assertThat(environment)
        .isEqualTo(
            CelEnvironment.newBuilder()
                .setSource(environment.source().get())
                .setVariables(
                    ImmutableSet.of(
                        VariableDecl.create(
                            "request",
                            TypeDecl.create("google.rpc.context.AttributeContext.Request")),
                        VariableDecl.create(
                            "map_var",
                            TypeDecl.newBuilder()
                                .setName("map")
                                .addParams(TypeDecl.create("string"), TypeDecl.create("string"))
                                .build())))
                .build());
    assertThat(environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void environment_parseErrors(@TestParameter EnvironmentParseErrorTestcase testCase) {
    CelEnvironmentException e =
        assertThrows(
            CelEnvironmentException.class, () -> ENVIRONMENT_PARSER.parse(testCase.yamlConfig));
    assertThat(e).hasMessageThat().isEqualTo(testCase.expectedErrorMessage);
  }

  @Test
  public void environment_extendErrors(@TestParameter EnvironmentExtendErrorTestCase testCase)
      throws Exception {
    CelEnvironment environment = ENVIRONMENT_PARSER.parse(testCase.yamlConfig);

    CelEnvironmentException e =
        assertThrows(
            CelEnvironmentException.class,
            () -> environment.extend(CEL_WITH_MESSAGE_TYPES, CelOptions.DEFAULT));
    assertThat(e).hasMessageThat().isEqualTo(testCase.expectedErrorMessage);
  }

  // Note: dangling comments in expressions below is to retain the newlines by preventing auto
  // formatter from compressing them in a single line.
  private enum EnvironmentParseErrorTestcase {
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
        "ERROR: <input>:5:14: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:seq]\n"
            + " |      params: 1\n"
            + " | .............^"),
    ILLEGAL_YAML_TYPE_WITH_INLINED_TYPE_TAGS(
        "variables:\n"
            + " - name: foo\n"
            + "   type_name: bar\n"
            + "   type:\n"
            + "     type_name: qux\n",
        "ERROR: <input>:4:4: 'type' tag cannot be used together with inlined 'type_name',"
            + " 'is_type_param' or 'params': type\n"
            + " |    type:\n"
            + " | ...^"),
    ILLEGAL_YAML_INLINED_TYPE_VALUE(
        "variables:\n" //
            + " - name: foo\n" //
            + "   type_name: 1\n",
        "ERROR: <input>:3:15: Got yaml node type tag:yaml.org,2002:int, wanted type(s)"
            + " [tag:yaml.org,2002:str !txt]\n"
            + " |    type_name: 1\n"
            + " | ..............^"),
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

    EnvironmentParseErrorTestcase(String yamlConfig, String expectedErrorMessage) {
      this.yamlConfig = yamlConfig;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }

  private enum EnvironmentExtendErrorTestCase {
    BAD_EXTENSION("extensions:\n" + "  - name: 'bad_name'", "Unrecognized extension: bad_name"),
    BAD_TYPE(
        "variables:\n" + "- name: 'bad_type'\n" + "  type:\n" + "    type_name: 'strings'",
        "Undefined type name: strings"),
    BAD_LIST(
        "variables:\n" + "  - name: 'bad_list'\n" + "    type:\n" + "      type_name: 'list'",
        "List type has unexpected param count: 0"),
    BAD_MAP(
        "variables:\n"
            + "  - name: 'bad_map'\n"
            + "    type:\n"
            + "      type_name: 'map'\n"
            + "      params:\n"
            + "        - type_name: 'string'",
        "Map type has unexpected param count: 1"),
    BAD_LIST_TYPE_PARAM(
        "variables:\n"
            + "  - name: 'bad_list_type_param'\n"
            + "    type:\n"
            + "      type_name: 'list'\n"
            + "      params:\n"
            + "        - type_name: 'number'",
        "Undefined type name: number"),
    BAD_MAP_TYPE_PARAM(
        "variables:\n"
            + "  - name: 'bad_map_type_param'\n"
            + "    type:\n"
            + "      type_name: 'map'\n"
            + "      params:\n"
            + "        - type_name: 'string'\n"
            + "        - type_name: 'optional'",
        "Undefined type name: optional"),
    BAD_RETURN(
        "functions:\n"
            + "  - name: 'bad_return'\n"
            + "    overloads:\n"
            + "      - id: 'zero_arity'\n"
            + "        return:\n"
            + "          type_name: 'mystery'",
        "Undefined type name: mystery"),
    BAD_OVERLOAD_TARGET(
        "functions:\n"
            + "  - name: 'bad_target'\n"
            + "    overloads:\n"
            + "      - id: 'unary_member'\n"
            + "        target:\n"
            + "          type_name: 'unknown'\n"
            + "        return:\n"
            + "          type_name: 'null_type'",
        "Undefined type name: unknown"),
    BAD_OVERLOAD_ARG(
        "functions:\n"
            + "  - name: 'bad_arg'\n"
            + "    overloads:\n"
            + "      - id: 'unary_global'\n"
            + "        args:\n"
            + "          - type_name: 'unknown'\n"
            + "        return:\n"
            + "          type_name: 'null_type'",
        "Undefined type name: unknown"),
    ;

    private final String yamlConfig;
    private final String expectedErrorMessage;

    EnvironmentExtendErrorTestCase(String yamlConfig, String expectedErrorMessage) {
      this.yamlConfig = yamlConfig;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum EnvironmentYamlResourceTestCase {
    EXTENDED_ENV(
        "environment/extended_env.yaml",
        CelEnvironment.newBuilder()
            .setName("extended-env")
            .setContainer("cel.expr")
            .setExtensions(
                ImmutableSet.of(
                    ExtensionConfig.of("optional", 2),
                    ExtensionConfig.of("math", Integer.MAX_VALUE)))
            .setVariables(
                VariableDecl.newBuilder()
                    .setName("msg")
                    .setType(TypeDecl.create("cel.expr.conformance.proto3.TestAllTypes"))
                    .build())
            .setFunctions(
                FunctionDecl.create(
                    "isEmpty",
                    ImmutableSet.of(
                        OverloadDecl.newBuilder()
                            .setId("wrapper_string_isEmpty")
                            .setTarget(TypeDecl.create("google.protobuf.StringValue"))
                            .setReturnType(TypeDecl.create("bool"))
                            .build(),
                        OverloadDecl.newBuilder()
                            .setId("list_isEmpty")
                            .setTarget(
                                TypeDecl.newBuilder()
                                    .setName("list")
                                    .addParams(
                                        TypeDecl.newBuilder()
                                            .setName("T")
                                            .setIsTypeParam(true)
                                            .build())
                                    .build())
                            .setReturnType(TypeDecl.create("bool"))
                            .build())))
            .build()),
    ;

    private final String yamlFileContent;
    private final CelEnvironment expectedEnvironment;

    EnvironmentYamlResourceTestCase(String yamlResourcePath, CelEnvironment expectedEnvironment) {
      try {
        this.yamlFileContent = readFile(yamlResourcePath);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      this.expectedEnvironment = expectedEnvironment;
    }
  }

  @Test
  public void environment_withYamlResource(@TestParameter EnvironmentYamlResourceTestCase testCase)
      throws Exception {
    CelEnvironment environment = ENVIRONMENT_PARSER.parse(testCase.yamlFileContent);

    // Empty out the parsed yaml source, as it's not relevant in the assertion here, and that it's
    // only obtainable through the yaml parser.
    environment = environment.toBuilder().setSource(Optional.empty()).build();
    assertThat(environment).isEqualTo(testCase.expectedEnvironment);
  }

  @Test
  public void lateBoundFunction_evaluate_callExpr() throws Exception {
    String configSource =
        "name: late_bound_function_config\n"
            + "functions:\n"
            + "  - name: 'test'\n"
            + "    overloads:\n"
            + "      - id: 'test_bool'\n"
            + "        args:\n"
            + "          - type_name: 'bool'\n"
            + "        return:\n"
            + "          type_name: 'bool'";
    CelEnvironment celEnvironment = ENVIRONMENT_PARSER.parse(configSource);
    Cel celDetails =
        CelFactory.standardCelBuilder()
            .addVar("a", SimpleType.INT)
            .addVar("b", SimpleType.INT)
            .addVar("c", SimpleType.INT)
            .build();
    Cel cel = celEnvironment.extend(celDetails, CelOptions.DEFAULT);
    CelAbstractSyntaxTree ast = cel.compile("a < 0 && b < 0 && c < 0 && test(a<0)").getAst();
    CelLateFunctionBindings bindings =
        CelLateFunctionBindings.from(
            CelFunctionBinding.from("test_bool", Boolean.class, result -> result));

    boolean result =
        (boolean) cel.createProgram(ast).eval(ImmutableMap.of("a", -1, "b", -1, "c", -4), bindings);

    assertThat(result).isTrue();
  }

  @Test
  public void lateBoundFunction_trace_callExpr_identifyFalseBranch() throws Exception {
    AtomicReference<CelExpr> capturedExpr = new AtomicReference<>();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (res instanceof Boolean && !(boolean) res && capturedExpr.get() == null) {
            capturedExpr.set(expr);
          }
        };

    String configSource =
        "name: late_bound_function_config\n"
            + "functions:\n"
            + "  - name: 'test'\n"
            + "    overloads:\n"
            + "      - id: 'test_bool'\n"
            + "        args:\n"
            + "          - type_name: 'bool'\n"
            + "        return:\n"
            + "          type_name: 'bool'";

    CelEnvironment celEnvironment = ENVIRONMENT_PARSER.parse(configSource);
    Cel celDetails =
        CelFactory.standardCelBuilder()
            .addVar("a", SimpleType.INT)
            .addVar("b", SimpleType.INT)
            .addVar("c", SimpleType.INT)
            .build();
    Cel cel = celEnvironment.extend(celDetails, CelOptions.DEFAULT);
    CelAbstractSyntaxTree ast = cel.compile("a < 0 && b < 0 && c < 0 && test(a<0)").getAst();
    CelLateFunctionBindings bindings =
        CelLateFunctionBindings.from(
            CelFunctionBinding.from("test_bool", Boolean.class, result -> result));

    boolean result =
        (boolean)
            cel.createProgram(ast)
                .trace(ImmutableMap.of("a", -1, "b", 1, "c", -4), bindings, listener);

    assertThat(result).isFalse();
    // Demonstrate that "b < 0" is what caused the expression to be false
    CelAbstractSyntaxTree subtree =
        CelAbstractSyntaxTree.newParsedAst(capturedExpr.get(), CelSource.newBuilder().build());
    assertThat(CelUnparserFactory.newUnparser().unparse(subtree)).isEqualTo("b < 0");
  }

  private static String readFile(String path) throws IOException {
    URL url = Resources.getResource(Ascii.toLowerCase(path));
    return Resources.toString(url, UTF_8);
  }
}
