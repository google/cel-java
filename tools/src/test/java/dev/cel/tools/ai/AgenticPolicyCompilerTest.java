package dev.cel.tools.ai;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.truth.Expect;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.expr.ai.AgentMessage;
import dev.cel.policy.testing.PolicyTestSuiteHelper;
import dev.cel.policy.testing.PolicyTestSuiteHelper.PolicyTestSuite;
import dev.cel.policy.testing.PolicyTestSuiteHelper.PolicyTestSuite.PolicyTestSection;
import dev.cel.policy.testing.PolicyTestSuiteHelper.PolicyTestSuite.PolicyTestSection.PolicyTestCase;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLateFunctionBindings;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class AgenticPolicyCompilerTest {
  @Rule
  public final Expect expect = Expect.create();

  private static final Cel CEL =
      AgenticPolicyEnvironment.newInstance();
  private static final AgenticPolicyCompiler POLICY_COMPILER = AgenticPolicyCompiler.newInstance(CEL);

  @Test
  public void runAgenticPolicyTestCases(@TestParameter AgenticPolicyTestCase testCase) throws Exception {
    CelAbstractSyntaxTree compiledPolicy = compilePolicy("policy/" + testCase.policyFilePath);
    PolicyTestSuite testSuite = PolicyTestSuiteHelper.readTestSuite("policy/" + testCase.policyTestCaseFilePath);
    runTests(CEL, compiledPolicy, testSuite);
  }

  private enum AgenticPolicyTestCase {
    PROMPT_INJECTION_TESTS(
        "prompt_injection.celpolicy",
        "prompt_injection_tests.yaml"
    ),
    REQUIRE_USER_CONFIRMATION_FOR_TOOL(
        "require_user_confirmation_for_tool.celpolicy",
        "require_user_confirmation_for_tool_tests.yaml"
    ),
    OPEN_WORLD_TOOL_REPLAY(
        "open_world_tool_replay.celpolicy",
        "open_world_tool_replay_tests.yaml"
    ),
    TRUST_CASCADING(
        "trust_cascading.celpolicy",
        "trust_cascading_tests.yaml"
    ),
    TIME_BOUND_APPROVAL(
        "time_bound_approval.celpolicy",
        "time_bound_approval_tests.yaml"
    );

    private final String policyFilePath;
    private final String policyTestCaseFilePath;

    AgenticPolicyTestCase(String policyFilePath, String policyTestCaseFilePath) {
      this.policyFilePath = policyFilePath;
      this.policyTestCaseFilePath = policyTestCaseFilePath;
    }
  }

  private static CelAbstractSyntaxTree compilePolicy(String policyPath)
      throws Exception {
    String policy = readFile(policyPath);
    return POLICY_COMPILER.compile(policy);
  }

  private static String readFile(String path) throws IOException {
    URL url = Resources.getResource(Ascii.toLowerCase(path));
    return Resources.toString(url, UTF_8);
  }

  private void runTests(Cel cel, CelAbstractSyntaxTree ast, PolicyTestSuite testSuite) {
    for (PolicyTestSection testSection : testSuite.getSection()) {
      for (PolicyTestCase testCase : testSection.getTests()) {
        String testName = String.format(
            "%s: %s", testSection.getName(), testCase.getName());
        try {
          ImmutableMap<String, Object> inputMap = testCase.toInputMap(cel);

          List<AgentMessage> history =
              inputMap.containsKey("_test_history")
                  ? (List<AgentMessage>) inputMap.get("_test_history")
                  : ImmutableList.of();

          @SuppressWarnings("Immutable")
          CelLateFunctionBindings bindings = CelLateFunctionBindings.from(
              CelFunctionBinding.from(
                  "agent_history",
                  ImmutableList.of(), // No args
                  (args) -> history
              )
          );

          Object evalResult = cel.createProgram(ast).eval(inputMap, bindings);
          Object expectedOutput = cel.createProgram(cel.compile(testCase.getOutput()).getAst()).eval();
          expect.withMessage(testName).that(evalResult).isEqualTo(expectedOutput);
        } catch (CelValidationException e) {
          expect.withMessage("Failed to compile test case for " + testName + ". Reason:\n" + e.getMessage()).fail();
        } catch (CelEvaluationException e) {
          expect.withMessage("Failed to evaluate test case for " + testName + ". Reason:\n" + e.getMessage()).fail();
        }
      }
    }
  }
}
