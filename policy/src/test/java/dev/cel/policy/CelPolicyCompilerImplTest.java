package dev.cel.policy;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static dev.cel.policy.PolicyTestHelper.readFromYaml;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
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
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection.PolicyTestCase;
import dev.cel.policy.PolicyTestHelper.TestYamlPolicy;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyCompilerImplTest {

  private static final CelPolicyParser POLICY_PARSER = CelPolicyParserFactory.newYamlParserBuilder()
      .build();
  private static final CelPolicyConfigParser POLICY_CONFIG_PARSER =
      CelPolicyParserFactory.newYamlConfigParser();
  private static final CelOptions CEL_OPTIONS = CelOptions.current().populateMacroCalls(true).build();

  @Test
  public void compileYamlPolicy_success(@TestParameter TestYamlPolicy yamlPolicy) throws Exception {
    // Read config and produce an environment to compile policies
    CelPolicySource configSource = yamlPolicy.readConfigYamlContent();
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    CelPolicySource policySource = yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);

    CelAbstractSyntaxTree ast = CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);

    assertThat(CelUnparserFactory.newUnparser().unparse(ast)).isEqualTo(yamlPolicy.getUnparsed());
  }

  @Test
  public void compileYamlPolicy_containsError_throws() throws Exception {
    // Read config and produce an environment to compile policies
    CelPolicySource configSource = readFromYaml("errors/config.yaml");
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    CelPolicySource policySource = readFromYaml("errors/policy.yaml");
    CelPolicy policy = POLICY_PARSER.parse(policySource);

    CelPolicyValidationException e = assertThrows(CelPolicyValidationException.class, () -> CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy));

    assertThat(e).hasMessageThat().contains("ERROR: errors/policy.yaml:19:19: undeclared reference to 'spec' (in container '')\n"
        + " |       expression: spec.labels\n"
        + " | ..................^\n"
        + "ERROR: errors/policy.yaml:21:50: mismatched input 'resource' expecting {'==', '!=', 'in', '<', '<=', '>=', '>', '&&', '||', '[', '(', ')', '.', '-', '?', '+', '*', '/', '%%'}\n"
        + " |       expression: variables.want.filter(l, !(lin resource.labels))\n"
        + " | .................................................^\n"
        + "ERROR: errors/policy.yaml:21:66: extraneous input ')' expecting <EOF>\n"
        + " |       expression: variables.want.filter(l, !(lin resource.labels))\n"
        + " | .................................................................^\n"
        + "ERROR: errors/policy.yaml:23:27: mismatched input '2' expecting {'}', ','}\n"
        + " |       expression: \"{1:305 2:569}\"\n"
        + " | ..........................^\n"
        + "ERROR: errors/policy.yaml:31:65: extraneous input ']' expecting ')'\n"
        + " |         \"missing one or more required labels: %s\".format(variables.missing])\n"
        + " | ................................................................^\n"
        + "ERROR: errors/policy.yaml:34:57: undeclared reference to 'format' (in container '')\n"
        + " |         \"invalid values provided on one or more labels: %s\".format([variables.invalid])\n"
        + " | ........................................................^");
  }

  @Test
  public void evaluateYamlPolicy_success(@TestParameter(valuesProvider = EvaluablePolicyTestDataProvider.class)
  EvaluablePolicyTestData testData) throws Exception {
    // Setup
    // Read config and produce an environment to compile policies
    CelPolicySource configSource = testData.yamlPolicy.readConfigYamlContent();
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    CelPolicySource policySource = testData.yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelAbstractSyntaxTree expectedOutputAst = cel.compile(testData.testCase.getOutput()).getAst();
    Object expectedOutput = cel.createProgram(expectedOutputAst).eval();

    // Act
    // Compile then evaluate the policy
    CelAbstractSyntaxTree compiledPolicyAst = CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);
    Map<String, Object> input = testData.testCase.getInput().entrySet().stream()
        .collect(toImmutableMap(Entry::getKey, e -> e.getValue().getValue()));
    Object evalResult = cel.createProgram(compiledPolicyAst).eval(input);

    // Assert
    // Note that policies may either produce an optional or a non-optional result,
    // if all the rules included nested ones can always produce a default result when none of the condition matches
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
    protected List<TestParameterValue> provideValues(Context context) throws Exception {
      ImmutableList.Builder<TestParameterValue> builder = ImmutableList.builder();
      for (TestYamlPolicy yamlPolicy : TestYamlPolicy.values()) {
        PolicyTestSuite testSuite = yamlPolicy.readTestYamlContent();
        for (PolicyTestSection testSection : testSuite.getSection()) {
          for (PolicyTestCase testCase : testSection.getTests()) {
            String testName = String.format("%s %s %s", yamlPolicy.getPolicyName(), testSection.getName(), testCase.getName());
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
        .setOptions(CEL_OPTIONS)
        .addFunctionBindings(
            CelFunctionBinding.from("locationCode_string", String.class, (ip) -> {
              switch (ip) {
              case "10.0.0.1":
                return "us";
              case "10.0.0.2":
                return "de";
              default:
                return "ir";
							}
            })
        )
        .build();
  }
}
