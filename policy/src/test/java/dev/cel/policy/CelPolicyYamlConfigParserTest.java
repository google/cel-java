package dev.cel.policy;

import com.google.common.collect.ImmutableSet;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelOptions;
import dev.cel.policy.CelPolicyConfig.ExtensionConfig;
import dev.cel.policy.CelPolicyConfig.FunctionDecl;
import dev.cel.policy.CelPolicyConfig.OverloadDecl;
import dev.cel.policy.CelPolicyConfig.TypeDecl;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

@RunWith(TestParameterInjector.class)
public final class CelPolicyYamlConfigParserTest {

  @Test
  public void config_setBasicProperties() {
    String yamlConfig = "name: hello\n" +
        "description: empty\n" +
        "container: pb.pkg\n";

    CelPolicyConfig policyConfig = CelPolicyYamlConfigParser.parse(yamlConfig);

    assertThat(policyConfig).isEqualTo(CelPolicyConfig.newBuilder()
        .setName("hello")
        .setDescription("empty")
        .setContainer("pb.pkg")
        .build());
  }

  @Test
  public void config_setExtensions() {
    String yamlConfig = "extensions:\n" +
        "  - name: \"bindings\"\n" +
        "  - name: \"encoders\"\n" +
        "  - name: \"math\"\n" +
        "  - name: \"optional\"\n" +
        "  - name: \"protos\"\n" +
        "  - name: \"strings\"\n" +
        "    version: 1";

    CelPolicyConfig policyConfig = CelPolicyYamlConfigParser.parse(yamlConfig);

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
    assertThat(policyConfig.toCel(CelOptions.DEFAULT)).isNotNull();
  }

  @Test
  public void config_setFunctions() {
    String yamlConfig = "functions:\n" +
        "  - name: \"coalesce\"\n" +
        "    overloads:\n" +
        "      - id: \"null_coalesce_int\"\n" +
        "        target:\n" +
        "          type_name: \"null_type\"\n" +
        "        args:\n" +
        "          - type_name: \"int\"\n" +
        "        return:\n" +
        "          type_name: \"int\"\n" +
        "      - id: \"coalesce_null_int\"\n" +
        "        args:\n" +
        "          - type_name: \"null_type\"\n" +
        "          - type_name: \"int\"\n" +
        "        return:\n" +
        "          type_name: \"int\"          \n" +
        "      - id: \"int_coalesce_int\"\n" +
        "        target: \n" +
        "          type_name: \"int\"\n" +
        "        args:\n" +
        "          - type_name: \"int\"\n" +
        "        return: \n" +
        "          type_name: \"int\"\n" +
        "      - id: \"optional_T_coalesce_T\"\n" +
        "        target: \n" +
        "          type_name: \"optional_type\"\n" +
        "          params:\n" +
        "            - type_name: \"T\"\n" +
        "              is_type_param: true\n" +
        "        args:\n" +
        "          - type_name: \"T\"\n" +
        "            is_type_param: true\n" +
        "        return: \n" +
        "          type_name: \"T\"\n" +
        "          is_type_param: true";

    CelPolicyConfig policyConfig = CelPolicyYamlConfigParser.parse(yamlConfig);

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
    assertThat(policyConfig.toCel(CelOptions.DEFAULT)).isNotNull();
  }

  private enum ConfigErrorTestCase {
    BAD_NAME("extensions:\n"
        + "  - name: 'bad_name'",
        "Unrecognized extension: bad_name"
    );

    private final String yamlConfig;
    private final String expectedErrorMessage;

    ConfigErrorTestCase(String yamlConfig, String expectedErrorMessage) {
      this.yamlConfig = yamlConfig;
      this.expectedErrorMessage = expectedErrorMessage;
    }
  }

  @Test
  public void configErrors(@TestParameter ConfigErrorTestCase testCase) {
    CelPolicyConfig policyConfig = CelPolicyYamlConfigParser.parse(testCase.yamlConfig);

    assertThrows(IllegalArgumentException.class, () -> policyConfig.toCel(CelOptions.DEFAULT));
  }
}