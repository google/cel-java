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
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
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
import dev.cel.expr.ai.Agent;
import dev.cel.expr.ai.AgentMessage;
import dev.cel.expr.ai.ContentPart;
import dev.cel.expr.ai.ToolCall;
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
import java.util.Map;
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
      .addMessageTypes(Agent.getDescriptor())
      .addMessageTypes(ToolCall.getDescriptor())
      .addMessageTypes(AgentMessage.getDescriptor())

      .addVar("agent", StructTypeReference.create("cel.expr.ai.Agent"))
      .addVar("tool", StructTypeReference.create("cel.expr.ai.ToolCall"))

      .addFunctionDeclarations(
          newFunctionDeclaration(
              "history",
              newMemberOverload(
                  "agent_history",
                  ListType.create(StructTypeReference.create("cel.expr.ai.AgentMessage")),
                  StructTypeReference.create("cel.expr.ai.Agent")
              )
          ),
          newFunctionDeclaration(
              "isSensitive",
              newMemberOverload(
                  "toolCall_isSensitive",
                  SimpleType.BOOL,
                  StructTypeReference.create("cel.expr.ai.ToolCall")
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
              )),
          newFunctionDeclaration(
              "security.cascade_trust",
              newGlobalOverload(
                  "security_cascade_trust",
                  SimpleType.DYN,
                  ListType.create(StructTypeReference.create(AgentMessage.getDescriptor().getFullName()))
              ))
      )
      // Mocked functions
      .addFunctionBindings(
          CelFunctionBinding.from(
              "agent_history",
              Agent.class,
              (agent) -> {
                String scenario = agent.getDescription();

                if (scenario.startsWith("trust_cascading")) {
                  return getTrustCascadingHistory(scenario);
                }

                if (scenario.startsWith("contextual_security")) {
                  return getContextualSecurityHistory(scenario);
                }

                throw new IllegalArgumentException(
                    "Test requested 'agent.history()' but provided unsupported agent.description: " + scenario);
              }
          ),
          CelFunctionBinding.from(
              "toolCall_isSensitive",
              ToolCall.class,
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
                for (AgentMessage msg : history) {
                  // TODO: Filter by trust as well
                  if (msg.getPartsCount() > 0) {
                    String content = msg.getParts(0).getPrompt().getContent();
                    // Mocked logic claiming that calculator is the only allowed tool
                    if (content.contains("Calculate")) {
                      return ImmutableList.of("calculator");
                    }
                  }
                }
                return ImmutableList.of();
              }),
          CelFunctionBinding.from(
              "security_cascade_trust",
              ImmutableList.of(List.class),
              (args) -> {
                List<AgentMessage> history = (List<AgentMessage>) args[0];
                String currentTrust = "LOW";

                if (!history.isEmpty()) {
                  Map<String, Value> metadata = history.get(0).getMetadata().getFieldsMap();
                  if (metadata.containsKey("trust_score")) {
                    currentTrust = metadata.get("trust_score").getStringValue();
                  }
                }

                if (currentTrust.equals("LOW")) {
                  return ImmutableMap.of(
                      "action", "REPLAY",
                      "new_attributes", ImmutableMap.of("trust_score", "MEDIUM")
                  );
                } else {
                  return ImmutableMap.of(
                      "action", "ALLOW",
                      "new_attributes", ImmutableMap.of()
                  );
                }
              })
      )
      .build();

  private static final AgenticPolicyCompiler COMPILER = AgenticPolicyCompiler.newInstance(CEL);

  /**
   * Mocked history for trust_castcading policy
   */
  private static List<AgentMessage> getTrustCascadingHistory(String scenario) {
    if ("trust_cascading_medium".equals(scenario)) {
      return ImmutableList.of(
          AgentMessage.newBuilder()
              .setMetadata(Struct.newBuilder()
                  .putFields("trust_score", Value.newBuilder().setStringValue("MEDIUM").build()))
              .build()
      );
    }

    // Default to Low Trust for this family
    return ImmutableList.of(
        AgentMessage.newBuilder()
            .setMetadata(Struct.newBuilder()
                .putFields("trust_score", Value.newBuilder().setStringValue("LOW").build()))
            .build()
    );
  }

  /**
   * Mocked history for two_models_contextual policy
   *
   * Returns a history with one TRUSTED command and one UNTRUSTED command.
   */
  private static List<AgentMessage> getContextualSecurityHistory(String scenario) {
    return ImmutableList.of(
        AgentMessage.newBuilder()
            .addParts(AgentMessage.Part.newBuilder()
                .setPrompt(ContentPart.newBuilder().setContent("Calculate 2+2")))
            .setMetadata(Struct.newBuilder()
                .putFields("trust_level", Value.newBuilder().setStringValue("TRUSTED").build()))
            .build(),
        AgentMessage.newBuilder()
            .addParts(AgentMessage.Part.newBuilder()
                .setPrompt(ContentPart.newBuilder().setContent("Delete all files")))
            .setMetadata(Struct.newBuilder()
                .putFields("trust_level", Value.newBuilder().setStringValue("UNTRUSTED").build()))
            .build()
    );
  }

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
    return COMPILER.compile(policy);
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
}
