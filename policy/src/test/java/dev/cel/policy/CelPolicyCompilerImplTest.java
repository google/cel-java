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
  public void compileYamlPolicy_containsError_throws() throws Exception {
    // Read config and produce an environment to compile policies
    String configSource = readFromYaml("errors/config.yaml");
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    String policyFilePath = "errors/policy.yaml";
    String policySource = readFromYaml(policyFilePath);
    CelPolicy policy = POLICY_PARSER.parse(policySource, policyFilePath);

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "ERROR: errors/policy.yaml:19:19: undeclared reference to 'spec' (in container '')\n"
                + " |       expression: spec.labels\n"
                + " | ..................^\n"
                + "ERROR: errors/policy.yaml:21:50: mismatched input 'resource' expecting {'==',"
                + " '!=', 'in', '<', '<=', '>=', '>', '&&', '||', '[', '(', ')', '.', '-', '?',"
                + " '+', '*', '/', '%%'}\n"
                + " |       expression: variables.want.filter(l, !(lin resource.labels))\n"
                + " | .................................................^\n"
                + "ERROR: errors/policy.yaml:21:66: extraneous input ')' expecting <EOF>\n"
                + " |       expression: variables.want.filter(l, !(lin resource.labels))\n"
                + " | .................................................................^\n"
                + "ERROR: errors/policy.yaml:23:27: mismatched input '2' expecting {'}', ','}\n"
                + " |       expression: \"{1:305 2:569}\"\n"
                + " | ..........................^\n"
                + "ERROR: errors/policy.yaml:31:65: extraneous input ']' expecting ')'\n"
                + " |         \"missing one or more required labels:"
                + " %s\".format(variables.missing])\n"
                + " | ................................................................^\n"
                + "ERROR: errors/policy.yaml:34:57: undeclared reference to 'format' (in container"
                + " '')\n"
                + " |         \"invalid values provided on one or more labels:"
                + " %s\".format([variables.invalid])\n"
                + " | ........................................................^\n"
                + "ERROR: errors/policy.yaml:35:24: found no matching overload for '_==_' applied"
                + " to '(bool, string)' (candidates: (%A0, %A0))\n"
                + " |     - condition: false == \"0\"\n"
                + " | .......................^");
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
  public void smokeTest() throws Exception {
    // String policyContent = "name: \"errors\"\n"
    //     + "rule:\n"
    //     + "  match:\n"
    //     + "    - output: \"'missing one or more required labels: %s'.format(variables.missing])\"";
    // String policyContent = "name: \"errors\"\"
    //     + "rule:\n"
    //     + "  match:\n"
    //     + "    - output: >\n"
    //     + "        'test'.format(variables.missing])";
    // String policyContent = "name: \"errors\"\n"
    //     + "rule:\n"
    //     + "  match:\n"
    //     + "    - output: >\n"
    //     + "        'test'.format(\n"
    //     + "        variables.missing])";
    String policyContent = "name: \"errors\"\n"
        + "rule:\n"
        + "  match:\n"
        + "    - output: >\n"
        + "        'test'\n"
        + "        .format(\n"
        + "        variables.missing])";
    CelPolicy policy = POLICY_PARSER.parse(policyContent);
    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(newCel()).build().compile(policy));

    assertThat(e).hasMessageThat().isEqualTo("");
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
}
