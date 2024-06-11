package dev.cel.policy;
import static com.google.common.truth.Truth.assertThat;

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
import dev.cel.policy.PolicyTestHelper.YamlPolicy;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyCompilerImplTest {

  private static final CelPolicyParser POLICY_PARSER = CelPolicyParserFactory.newYamlParserBuilder()
      .build();
  private static final CelPolicyConfigParser POLICY_CONFIG_PARSER =
      CelPolicyParserFactory.newYamlConfigParser();

  @Test
  public void compileYamlPolicy_success(@TestParameter YamlPolicy yamlPolicy)
      throws Exception {
    // Read config and produce an environment to compile policies
    CelPolicySource configSource = yamlPolicy.readConfigYamlContent();
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CelOptions.newBuilder().build());
    // Read the policy source
    CelPolicySource policySource = yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);

    CelAbstractSyntaxTree ast = CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);

    assertThat(ast).isNotNull();
  }

  private static final class EvaluablePolicyTestData {
    private final YamlPolicy yamlPolicy;
    private final PolicyTestCase testCase;

    private EvaluablePolicyTestData(YamlPolicy yamlPolicy, PolicyTestCase testCase) {
      this.yamlPolicy = yamlPolicy;
      this.testCase = testCase;
    }
  }

  private static final class PolicyTestDataProvider extends TestParameterValuesProvider {

    @Override
    protected List<TestParameterValue> provideValues(Context context) throws Exception {
      ImmutableList.Builder<TestParameterValue> builder = ImmutableList.builder();
      for (YamlPolicy yamlPolicy : YamlPolicy.values()) {
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

  @Test
  public void evaluateYamlPolicy_success(@TestParameter(valuesProvider = PolicyTestDataProvider.class)
                                           EvaluablePolicyTestData testData) throws Exception{
    // Setup
    // Read config and produce an environment to compile policies
    CelPolicySource configSource = testData.yamlPolicy.readConfigYamlContent();
    CelPolicyConfig policyConfig = POLICY_CONFIG_PARSER.parse(configSource);
    Cel cel = policyConfig.extend(newCel(), CelOptions.current().populateMacroCalls(true).build());
    // Read the policy source
    CelPolicySource policySource = testData.yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelAbstractSyntaxTree expectedOutputAst = cel.compile(testData.testCase.getOutput()).getAst();
    Object expectedOutput = cel.createProgram(expectedOutputAst).eval();

    // Act
    // Compile then evaluate the policy
    CelAbstractSyntaxTree compiledPolicyAst = CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);
    // String unparsed = CelUnparserFactory.newUnparser().unparse(compiledPolicyAst);
    Object evalResult = cel.createProgram(compiledPolicyAst).eval(testData.testCase.getInput());

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

  private static Cel newCel() {
    return CelFactory.standardCelBuilder()
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
        .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
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
