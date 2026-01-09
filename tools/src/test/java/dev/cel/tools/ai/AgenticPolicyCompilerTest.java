package dev.cel.tools.ai;

import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static dev.cel.common.CelOverloadDecl.newMemberOverload;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.truth.Expect;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.expr.ai.AgentMessage;
import dev.cel.expr.ai.AgentRequestContext;
import dev.cel.expr.ai.McpToolCall;
import dev.cel.parser.CelStandardMacro;
import dev.cel.policy.testing.PolicyTestSuiteHelper;
import dev.cel.policy.testing.PolicyTestSuiteHelper.PolicyTestSuite;
import dev.cel.policy.testing.PolicyTestSuiteHelper.PolicyTestSuite.PolicyTestSection;
import dev.cel.policy.testing.PolicyTestSuiteHelper.PolicyTestSuite.PolicyTestSection.PolicyTestCase;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
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

  private static final Cel CEL = CelFactory.standardCelBuilder()
      .setContainer(CelContainer.ofName("cel.expr.ai"))
      .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
      .addMessageTypes(AgentRequestContext.getDescriptor())
      .addVar("tool", StructTypeReference.create("cel.expr.ai.McpToolCall"))
      .addVar("ctx", StructTypeReference.create("cel.expr.ai.AgentRequestContext"))
      .addFunctionDeclarations(
          newFunctionDeclaration(
              "isSensitive",
              newMemberOverload(
                  "mcpToolCall_isSensitive",
                  SimpleType.BOOL,
                  StructTypeReference.create("cel.expr.ai.McpToolCall")
              )),
          newFunctionDeclaration(
              "security.classifyInjection",
              newGlobalOverload(
                  "classifyInjection_string",
                  SimpleType.DOUBLE,
                  SimpleType.STRING
              )),
          newFunctionDeclaration(
          "security.computePrivilegedPlan",
          newGlobalOverload(
              "computePrivilegedPlan_agentMessage",
              ListType.create(SimpleType.STRING),
              ListType.create(StructTypeReference.create(AgentMessage.getDescriptor().getFullName()))
          ))
      )
      // Mocked example bindings
      .addFunctionBindings(
          CelFunctionBinding.from(
              "mcpToolCall_isSensitive",
              McpToolCall.class,
              (tool) -> tool.getName().contains("PII")),
          CelFunctionBinding.from(
              "classifyInjection_string",
              ImmutableList.of(String.class),
              (args) -> {
                String input = (String) args[0];
                if (input.contains("INJECTION_ATTACK")) return 0.95;
                if (input.contains("SUSPICIOUS")) return 0.6;
                return 0.1;
              }),
          CelFunctionBinding.from(
              "computePrivilegedPlan_agentMessage",
              ImmutableList.of(List.class),
              (args) -> {
                List<AgentMessage> history = (List<AgentMessage>) args[0];
                // Mock Logic: Scan trusted history for intent
                for (AgentMessage msg : history) {
                  // Check if content implies calculation
                  String content = msg.getParts(0).getPrompt().getContent();
                  if (content.contains("Calculate")) {
                    return ImmutableList.of("calculator");
                  }
                }

                // Signal nothing is allowed
                return ImmutableList.of();
              })
      )
      .build();

  private static final AgenticPolicyCompiler COMPILER = AgenticPolicyCompiler.newInstance(CEL);

  @Test
  public void runAgenticPolicyTestCases(@TestParameter AgenticPolicyTestCase testCase) throws Exception {
    CelAbstractSyntaxTree compiledPolicy = compilePolicy(testCase.policyFilePath);
    PolicyTestSuite testSuite = PolicyTestSuiteHelper.readTestSuite(testCase.policyTestCaseFilePath);

    runTests(CEL, compiledPolicy, testSuite);
  }

  private enum AgenticPolicyTestCase {
    REQUIRE_USER_CONFIRMATION_FOR_TOOL(
        "require_user_confirmation_for_tool.celpolicy",
        "require_user_confirmation_for_tool_tests.yaml"
    ),
    PROMPT_INJECTION_TESTS(
        "prompt_injection.celpolicy",
        "prompt_injection_tests.yaml"
    ),
    RISKY_AGENT_REPLAY(
        "risky_agent_replay.celpolicy",
        "risky_agent_replay_tests.yaml"
    ),
    TOOL_WALLED_GARDEN(
        "tool_walled_garden.celpolicy",
        "tool_walled_garden_tests.yaml"
    ),
    TWO_MODELS_CONTEXTUAL(
        "two_models_contextual.celpolicy",
        "two_models_contextual_tests.yaml"
    ),
    TRUST_CASCADING(
        "trust_cascading.celpolicy",
        "trust_cascading_tests.yaml"
    )
    ;

    private final String policyFilePath;
    private final String policyTestCaseFilePath;

    AgenticPolicyTestCase(
        String policyFilePath,
        String policyTestCaseFilePath
    ) {
      this.policyFilePath = policyFilePath;
      this.policyTestCaseFilePath = policyTestCaseFilePath;
    }
  }

  private static CelAbstractSyntaxTree compilePolicy(String policyPath)
      throws Exception {
    String policy = readFile(policyPath);
    return COMPILER.compile(policy);
  }

  private void runTests(Cel cel, CelAbstractSyntaxTree ast, PolicyTestSuite testSuite)
      {
    for (PolicyTestSection testSection : testSuite.getSection()) {
      for (PolicyTestCase testCase : testSection.getTests()) {
        String testName = String.format(
            "%s: %s", testSection.getName(), testCase.getName());

        try {
          ImmutableMap<String, Object> inputMap = testCase.toInputMap(cel);
          Object evalResult = cel.createProgram(ast).eval(inputMap);
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

  private static String readFile(String path) throws IOException {
    URL url = Resources.getResource(Ascii.toLowerCase(path));
    return Resources.toString(url, UTF_8);
  }
}
