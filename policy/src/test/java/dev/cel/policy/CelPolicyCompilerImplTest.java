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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.truth.Truth.assertThat;
import static dev.cel.policy.PolicyTestHelper.readFromYaml;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameterValue;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.policy.PolicyTestHelper.K8sTagHandler;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection.PolicyTestCase;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection.PolicyTestCase.PolicyTestInput;
import dev.cel.policy.PolicyTestHelper.TestYamlPolicy;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyCompilerImplTest {

  private static final CelPolicyParser POLICY_PARSER =
      CelPolicyParserFactory.newYamlParserBuilder().addTagVisitor(new K8sTagHandler()).build();
  private static final CelPolicyConfigParser POLICY_CONFIG_PARSER =
      CelPolicyParserFactory.newYamlConfigParser();
  private static final CelOptions CEL_OPTIONS =
      CelOptions.current().populateMacroCalls(true).build();

  @Test
  public void compileYamlPolicy_success(@TestParameter TestYamlPolicy yamlPolicy) throws Exception {
    // Read config and produce an environment to compile policies
    String configSource = yamlPolicy.readConfigYamlContent();
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    String policySource = yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);

    CelAbstractSyntaxTree ast =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);

    assertThat(CelUnparserFactory.newUnparser().unparse(ast)).isEqualTo(yamlPolicy.getUnparsed());
  }

  @Test
  public void compileYamlPolicy_containsCompilationError_throws(
      @TestParameter TestErrorYamlPolicy testCase) throws Exception {
    // Read config and produce an environment to compile policies
    String configSource = testCase.readConfigYamlContent();
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    String policySource = testCase.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource, testCase.getPolicyFilePath());

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy));

    assertThat(e).hasMessageThat().isEqualTo(testCase.readExpectedErrorsBaseline());
  }

  @Test
  public void compileYamlPolicy_multilineContainsError_throws(
      @TestParameter MultilineErrorTest testCase) throws Exception {
    String policyContent = testCase.yaml;
    CelPolicy policy = POLICY_PARSER.parse(policyContent);

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(newCel()).build().compile(policy));

    assertThat(e).hasMessageThat().isEqualTo(testCase.expected);
  }

  @Test
  public void compileYamlPolicy_exceedsDefaultAstDepthLimit_throws() throws Exception {
    String longExpr =
        "0+1+2+3+4+5+6+7+8+9+10+11+12+13+14+15+16+17+18+19+20+21+22+23+24+25+26+27+28+29+30+31+32+33+34+35+36+37+38+39+40+41+42+43+44+45+46+47+48+49+50";
    String policyContent =
        String.format(
            "name: deeply_nested_ast\n" + "rule:\n" + "  match:\n" + "    - output: %s", longExpr);
    CelPolicy policy = POLICY_PARSER.parse(policyContent);

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(newCel()).build().compile(policy));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("ERROR: <input>:-1:0: AST's depth exceeds the configured limit: 50.");
  }

  @Test
  public void compileYamlPolicy_astDepthLimitCheckDisabled_doesNotThrow() throws Exception {
    String longExpr =
        "0+1+2+3+4+5+6+7+8+9+10+11+12+13+14+15+16+17+18+19+20+21+22+23+24+25+26+27+28+29+30+31+32+33+34+35+36+37+38+39+40+41+42+43+44+45+46+47+48+49+50";
    String policyContent =
        String.format(
            "name: deeply_nested_ast\n" + "rule:\n" + "  match:\n" + "    - output: %s", longExpr);
    CelPolicy policy = POLICY_PARSER.parse(policyContent);

    CelAbstractSyntaxTree ast =
        CelPolicyCompilerFactory.newPolicyCompiler(newCel())
            .setAstDepthLimit(-1)
            .build()
            .compile(policy);
    assertThat(ast).isNotNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void evaluateYamlPolicy_withCanonicalTestData(
      @TestParameter(valuesProvider = EvaluablePolicyTestDataProvider.class)
          EvaluablePolicyTestData testData)
      throws Exception {
    // Setup
    // Read config and produce an environment to compile policies
    String configSource = testData.yamlPolicy.readConfigYamlContent();
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    String policySource = testData.yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelAbstractSyntaxTree expectedOutputAst = cel.compile(testData.testCase.getOutput()).getAst();
    Object expectedOutput = cel.createProgram(expectedOutputAst).eval();

    // Act
    // Compile then evaluate the policy
    CelAbstractSyntaxTree compiledPolicyAst =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);
    ImmutableMap.Builder<String, Object> inputBuilder = ImmutableMap.builder();
    for (Map.Entry<String, PolicyTestInput> entry : testData.testCase.getInput().entrySet()) {
      String exprInput = entry.getValue().getExpr();
      if (isNullOrEmpty(exprInput)) {
        inputBuilder.put(entry.getKey(), entry.getValue().getValue());
      } else {
        CelAbstractSyntaxTree exprInputAst = cel.compile(exprInput).getAst();
        inputBuilder.put(entry.getKey(), cel.createProgram(exprInputAst).eval());
      }
    }
    Object evalResult = cel.createProgram(compiledPolicyAst).eval(inputBuilder.buildOrThrow());

    // Assert
    // Note that policies may either produce an optional or a non-optional result,
    // if all the rules included nested ones can always produce a default result when none of the
    // condition matches
    if (testData.yamlPolicy.producesOptionalResult()) {
      Optional<Object> policyOutput = (Optional<Object>) evalResult;
      if (policyOutput.isPresent()) {
        assertThat(policyOutput).hasValue(expectedOutput);
      } else {
        assertThat(policyOutput).isEmpty();
      }
    } else {
      assertThat(evalResult).isEqualTo(expectedOutput);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void evaluateYamlPolicy_nestedRuleProducesOptionalOutput() throws Exception {
    Cel cel = newCel();
    String policySource =
        "name: nested_rule_with_optional_result\n"
            + "rule:\n"
            + "  match:\n"
            + "    - rule:\n"
            + "        match:\n"
            + "          - condition: 'true'\n"
            + "            output: 'optional.of(true)'\n";
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelAbstractSyntaxTree compiledPolicyAst =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);

    Optional<Object> evalResult = (Optional<Object>) cel.createProgram(compiledPolicyAst).eval();

    // Result is Optional<Optional<Object>>
    assertThat(evalResult).hasValue(Optional.of(true));
  }

  private static final class EvaluablePolicyTestData {
    private final TestYamlPolicy yamlPolicy;
    private final PolicyTestCase testCase;

    private EvaluablePolicyTestData(TestYamlPolicy yamlPolicy, PolicyTestCase testCase) {
      this.yamlPolicy = yamlPolicy;
      this.testCase = testCase;
    }
  }

  private static final class EvaluablePolicyTestDataProvider extends TestParameterValuesProvider {

    @Override
    protected ImmutableList<TestParameterValue> provideValues(Context context) throws Exception {
      ImmutableList.Builder<TestParameterValue> builder = ImmutableList.builder();
      for (TestYamlPolicy yamlPolicy : TestYamlPolicy.values()) {
        PolicyTestSuite testSuite = yamlPolicy.readTestYamlContent();
        for (PolicyTestSection testSection : testSuite.getSection()) {
          for (PolicyTestCase testCase : testSection.getTests()) {
            String testName =
                String.format(
                    "%s %s %s",
                    yamlPolicy.getPolicyName(), testSection.getName(), testCase.getName());
            builder.add(
                value(new EvaluablePolicyTestData(yamlPolicy, testCase)).withName(testName));
          }
        }
      }

      return builder.build();
    }
  }

  private static Cel newCel() {
    return CelFactory.standardCelBuilder()
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
        .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
        .addMessageTypes(TestAllTypes.getDescriptor())
        .setOptions(CEL_OPTIONS)
        .addFunctionBindings(
            CelFunctionBinding.from(
                "locationCode_string",
                String.class,
                (ip) -> {
                  switch (ip) {
                    case "10.0.0.1":
                      return "us";
                    case "10.0.0.2":
                      return "de";
                    default:
                      return "ir";
                  }
                }))
        .build();
  }

  private enum MultilineErrorTest {
    SINGLE_FOLDED(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: >\n"
            + "        'test'.format(variables.missing])",
        "ERROR: <input>:5:40: extraneous input ']' expecting ')'\n"
            + " |         'test'.format(variables.missing])\n"
            + " | .......................................^"),
    DOUBLE_FOLDED(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: >\n"
            + "        'test'.format(\n"
            + "        variables.missing])",
        "ERROR: <input>:6:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    TRIPLE_FOLDED(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: >\n"
            + "        'test'.\n"
            + "        format(\n"
            + "        variables.missing])",
        "ERROR: <input>:7:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    SINGLE_LITERAL(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: |\n"
            + "        'test'.format(variables.missing])",
        "ERROR: <input>:5:40: extraneous input ']' expecting ')'\n"
            + " |         'test'.format(variables.missing])\n"
            + " | .......................................^"),
    DOUBLE_LITERAL(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: |\n"
            + "        'test'.format(\n"
            + "        variables.missing])",
        "ERROR: <input>:6:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    TRIPLE_LITERAL(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: |\n"
            + "        'test'.\n"
            + "        format(\n"
            + "        variables.missing])",
        "ERROR: <input>:7:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    ;

    private final String yaml;
    private final String expected;

    MultilineErrorTest(String yaml, String expected) {
      this.yaml = yaml;
      this.expected = expected;
    }
  }

  private enum TestErrorYamlPolicy {
    COMPILE_ERRORS("compile_errors"),
    COMPOSE_ERRORS_CONFLICTING_OUTPUT("compose_errors_conflicting_output"),
    COMPOSE_ERRORS_CONFLICTING_SUBRULE("compose_errors_conflicting_subrule"),
    ERRORS_UNREACHABLE("errors_unreachable");

    private final String name;
    private final String policyFilePath;

    private String getPolicyFilePath() {
      return policyFilePath;
    }

    private String readPolicyYamlContent() throws IOException {
      return readFromYaml(String.format("%s/policy.yaml", name));
    }

    private String readConfigYamlContent() throws IOException {
      return readFromYaml(String.format("%s/config.yaml", name));
    }

    private String readExpectedErrorsBaseline() throws IOException {
      return readFromYaml(String.format("%s/expected_errors.baseline", name));
    }

    TestErrorYamlPolicy(String name) {
      this.name = name;
      this.policyFilePath = String.format("%s/policy.yaml", name);
    }
  }
}
