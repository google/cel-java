package dev.cel.policy;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.rpc.context.AttributeContext;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelOptions;
import dev.cel.policy.CelPolicyConfig.ExtensionConfig;
import dev.cel.policy.CelPolicyConfig.FunctionDecl;
import dev.cel.policy.CelPolicyConfig.OverloadDecl;
import dev.cel.policy.CelPolicyConfig.TypeDecl;
import dev.cel.policy.CelPolicyConfig.VariableDecl;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyYamlConfigParserTest {

  private static final Cel CEL = CelFactory.standardCelBuilder()
      .addMessageTypes(AttributeContext.Request.getDescriptor())
      .build();

  private static final CelPolicyConfigParser CEL_POLICY_CONFIG_PARSER =
      CelPolicyParserFactory.newYamlConfigParser();

  @Test
  public void config_setBasicProperties() throws Exception {
    String yamlConfig = "name: hello\n" +
        "description: empty\n" +
        "container: pb.pkg\n";

    CelPolicyConfig policyConfig = CEL_POLICY_CONFIG_PARSER.parse(
        CelPolicySource.fromText(yamlConfig));

    assertThat(policyConfig).isEqualTo(CelPolicyConfig.newBuilder()
        .setName("hello")
        .setDescription("empty")
        .setContainer("pb.pkg")
        .build());
  }

  @Test
  public void config_setExtensions() throws Exception {
    String yamlConfig = "extensions:\n" +
        "  - name: 'bindings'\n" +
        "  - name: 'encoders'\n" +
        "  - name: 'math'\n" +
        "  - name: 'optional'\n" +
        "  - name: 'protos'\n" +
        "  - name: 'strings'\n" +
        "    version: 1";

    CelPolicyConfig policyConfig = CEL_POLICY_CONFIG_PARSER.parse(
        CelPolicySource.fromText(yamlConfig));

    assertThat(policyConfig).isEqualTo(CelPolicyConfig.newBuilder()
        .setExtensions(
            ImmutableSet.of(
                ExtensionConfig.of("bindings"),
                ExtensionConfig.of("encoders"),
                ExtensionConfig.of("math"),
                ExtensionConfig.of("optional"),
                ExtensionConfig.of("protos"),
                ExtensionConfig.of("strings", 1)
            )
        )
        .build());
    assertThat(policyConfig.extend(CEL, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void config_setFunctions() throws Exception {
    String yamlConfig = "functions:\n" +
        "  - name: 'coalesce'\n" +
        "    overloads:\n" +
        "      - id: 'null_coalesce_int'\n" +
        "        target:\n" +
        "          type_name: 'null_type'\n" +
        "        args:\n" +
        "          - type_name: 'int'\n" +
        "        return:\n" +
        "          type_name: 'int'\n" +
        "      - id: 'coalesce_null_int'\n" +
        "        args:\n" +
        "          - type_name: 'null_type'\n" +
        "          - type_name: 'int'\n" +
        "        return:\n" +
        "          type_name: 'int'          \n" +
        "      - id: 'int_coalesce_int'\n" +
        "        target: \n" +
        "          type_name: 'int'\n" +
        "        args:\n" +
        "          - type_name: 'int'\n" +
        "        return: \n" +
        "          type_name: 'int'\n" +
        "      - id: 'optional_T_coalesce_T'\n" +
        "        target: \n" +
        "          type_name: 'optional_type'\n" +
        "          params:\n" +
        "            - type_name: 'T'\n" +
        "              is_type_param: true\n" +
        "        args:\n" +
        "          - type_name: 'T'\n" +
        "            is_type_param: true\n" +
        "        return: \n" +
        "          type_name: 'T'\n" +
        "          is_type_param: true";

    CelPolicyConfig policyConfig = CEL_POLICY_CONFIG_PARSER.parse(
        CelPolicySource.fromText(yamlConfig));

    assertThat(policyConfig).isEqualTo(CelPolicyConfig.newBuilder()
        .setFunctions(
            ImmutableSet.of(
                FunctionDecl.create("coalesce",
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
                                TypeDecl.create("null_type"),
                                TypeDecl.create("int")
                            )
                            .setReturnType(TypeDecl.create("int"))
                            .build(),
                        OverloadDecl.newBuilder()
                            .setId("int_coalesce_int")
                            .setTarget(TypeDecl.create("int"))
                            .addArguments(
                                TypeDecl.create("int")
                            )
                            .setReturnType(TypeDecl.create("int"))
                            .build(),
                        OverloadDecl.newBuilder()
                            .setId("optional_T_coalesce_T")
                            .setTarget(
                                TypeDecl.newBuilder()
                                    .setName("optional_type")
                                    .addParams(
                                        TypeDecl.newBuilder().setName("T").setIsTypeParam(true)
                                            .build()).build()
                            )
                            .addArguments(
                                TypeDecl.newBuilder().setName("T").setIsTypeParam(true).build()
                            )
                            .setReturnType(
                                TypeDecl.newBuilder().setName("T").setIsTypeParam(true).build()
                            )
                            .build()
                    )
                )
            )
        )
        .build());
    assertThat(policyConfig.extend(CEL, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void config_setMapVariable() throws Exception {
    String yamlConfig = "variables:\n"
        + "- name: 'request'\n"
        + "  type:\n"
        + "    type_name: 'map'\n"
        + "    params:\n"
        + "      - type_name: 'string'\n"
        + "      - type_name: 'dyn'";

    CelPolicyConfig policyConfig = CEL_POLICY_CONFIG_PARSER.parse(
        CelPolicySource.fromText(yamlConfig));

    assertThat(policyConfig).isEqualTo(CelPolicyConfig.newBuilder()
        .setVariables(
            ImmutableSet.of(
                VariableDecl.create("request",
                    TypeDecl.newBuilder()
                        .setName("map")
                        .addParams(
                            TypeDecl.create("string"),
                            TypeDecl.create("dyn")
                        ).build()
                ))
        )
        .build());
    assertThat(policyConfig.extend(CEL, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void config_setMessageVariable() throws Exception {
    String yamlConfig = "variables:\n"
        + "- name: 'request'\n"
        + "  type:\n"
        + "    type_name: 'google.rpc.context.AttributeContext.Request'";

    CelPolicyConfig policyConfig = CEL_POLICY_CONFIG_PARSER.parse(
        CelPolicySource.fromText(yamlConfig));

    assertThat(policyConfig).isEqualTo(CelPolicyConfig.newBuilder()
        .setVariables(
            ImmutableSet.of(
                VariableDecl.create("request",
                    TypeDecl.create("google.rpc.context.AttributeContext.Request")
                ))
        )
        .build());
    assertThat(policyConfig.extend(CEL, CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void config_parseErrors(@TestParameter ConfigParseErrorTestcase testCase) {
    CelPolicyValidationException e = assertThrows(CelPolicyValidationException.class,
        () -> CEL_POLICY_CONFIG_PARSER.parse(
            CelPolicySource.fromText(testCase.yamlConfig)));
    assertThat(e).hasMessageThat().isEqualTo(testCase.expectedErrorMessage);
  }

  @Test
  public void config_extendErrors(@TestParameter ConfigExtendErrorTestCase testCase)
      throws Exception {
    CelPolicyConfig policyConfig = CEL_POLICY_CONFIG_PARSER.parse(
        CelPolicySource.fromText(testCase.yamlConfig));

    CelPolicyValidationException e = assertThrows(CelPolicyValidationException.class,
        () -> policyConfig.extend(CEL, CelOptions.DEFAULT));
    assertThat(e).hasMessageThat().isEqualTo(testCase.expectedErrorMessage);
  }

  private enum ConfigParseErrorTestcase {
    MISSING_VARIABLE_PROPERTIES("variables:\n"
        + " - illegal: 2",
        "ERROR: <input>:2:4: Unsupported variable tag: illegal\n" +
                " |  - illegal: 2\n" +
                " | ...^\n" +
                "ERROR: <input>:2:4: Missing required attribute(s): name, type\n" +
                " |  - illegal: 2\n" +
                " | ...^"),
    MISSING_OVERLOAD_RETURN("functions:\n"
        + "  - name: 'missing_return'\n"
        + "    overloads:\n"
        + "      - id: 'zero_arity'\n",
            "ERROR: <input>:4:9: Missing required attribute(s): return\n" +
            " |       - id: 'zero_arity'\n" +
            " | ........^"),
    MISSING_FUNCTION_NAME("functions:\n"
        + "  - overloads:\n"
        + "      - id: 'foo'\n"
        + "        return:\n"
        + "          type_name: 'string'\n",
            "ERROR: <input>:2:5: Missing required attribute(s): name\n" +
            " |   - overloads:\n" +
            " | ....^"),
    MISSING_OVERLOAD("functions:\n"
        + "  - name: 'missing_overload'\n", "ERROR: <input>:2:5: Missing required attribute(s): overloads\n" +
            " |   - name: 'missing_overload'\n" +
            " | ....^"),
    MISSING_EXTENSION_NAME("extensions:\n"
        + "- version: 0", "ERROR: <input>:2:3: Missing required attribute(s): name\n" +
            " | - version: 0\n" +
            " | ..^"),
    ;

    private final String yamlConfig;
    private final String expectedErrorMessage;

    ConfigParseErrorTestcase(String yamlConfig, String expectedErrorMessage) {
      this.yamlConfig = yamlConfig;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }

  private enum ConfigExtendErrorTestCase {
    BAD_EXTENSION("extensions:\n"
        + "  - name: 'bad_name'",
        "Unrecognized extension: bad_name"
    ),
    BAD_TYPE("variables:\n"
        + "- name: 'bad_type'\n"
        + "  type:\n"
        + "    type_name: 'strings'", "Undefined type name: strings"),
    BAD_LIST("variables:\n"
        + "  - name: 'bad_list'\n"
        + "    type:\n"
        + "      type_name: 'list'", "List type has unexpected param count: 0"),
    BAD_MAP("variables:\n"
        + "  - name: 'bad_map'\n"
        + "    type:\n"
        + "      type_name: 'map'\n"
        + "      params:\n"
        + "        - type_name: 'string'", "Map type has unexpected param count: 1"),
    BAD_LIST_TYPE_PARAM("variables:\n"
        + "  - name: 'bad_list_type_param'\n"
        + "    type:\n"
        + "      type_name: 'list'\n"
        + "      params:\n"
        + "        - type_name: 'number'", "Undefined type name: number"),
    BAD_MAP_TYPE_PARAM("variables:\n"
        + "  - name: 'bad_map_type_param'\n"
        + "    type:\n"
        + "      type_name: 'map'\n"
        + "      params:\n"
        + "        - type_name: 'string'\n"
        + "        - type_name: 'optional'", "Undefined type name: optional"),
    BAD_RETURN("functions:\n"
        + "  - name: 'bad_return'\n"
        + "    overloads:\n"
        + "      - id: 'zero_arity'\n"
        + "        return:\n"
        + "          type_name: 'mystery'", "Undefined type name: mystery"),
    BAD_OVERLOAD_TARGET("functions:\n"
        + "  - name: 'bad_target'\n"
        + "    overloads:\n"
        + "      - id: 'unary_member'\n"
        + "        target:\n"
        + "          type_name: 'unknown'\n"
        + "        return:\n"
        + "          type_name: 'null_type'", "Undefined type name: unknown"),
    BAD_OVERLOAD_ARG("functions:\n"
        + "  - name: 'bad_arg'\n"
        + "    overloads:\n"
        + "      - id: 'unary_global'\n"
        + "        args:\n"
        + "          - type_name: 'unknown'\n"
        + "        return:\n"
        + "          type_name: 'null_type'", "Undefined type name: unknown"),
    ;

    private final String yamlConfig;
    private final String expectedErrorMessage;

    ConfigExtendErrorTestCase(String yamlConfig, String expectedErrorMessage) {
      this.yamlConfig = yamlConfig;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }
}
