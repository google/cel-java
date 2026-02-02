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
import dev.cel.expr.ai.Agent;
import dev.cel.expr.ai.AgentMessage;
import dev.cel.expr.ai.Finding;
import dev.cel.expr.ai.Tool;
import dev.cel.expr.ai.ToolAnnotations;
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
      .addMessageTypes(Tool.getDescriptor())
      .addMessageTypes(ToolAnnotations.getDescriptor())
      .addMessageTypes(AgentMessage.getDescriptor())
      .addMessageTypes(Finding.getDescriptor())
      .addVar("agent.input", StructTypeReference.create("cel.expr.ai.AgentMessage"))
      .addVar("tool.name", SimpleType.STRING)
      .addVar("tool.annotations", StructTypeReference.create("cel.expr.ai.ToolAnnotations"))
      .addVar("tool.call", StructTypeReference.create("cel.expr.ai.ToolCall"))
      .addFunctionDeclarations(
          newFunctionDeclaration(
              "ai.finding",
              newGlobalOverload(
                  "ai_finding_string_double",
                  StructTypeReference.create("cel.expr.ai.Finding"),
                  SimpleType.STRING,
                  SimpleType.DOUBLE
              )
          ),
          newFunctionDeclaration(
              "threats",
              newMemberOverload(
                  "agent_message_threats",
                  ListType.create(StructTypeReference.create("cel.expr.ai.Finding")),
                  StructTypeReference.create("cel.expr.ai.AgentMessage")
              )
          ),
          newFunctionDeclaration(
              "sensitivityLabel",
              newMemberOverload(
                  "tool_call_sensitivity_label",
                  ListType.create(StructTypeReference.create("cel.expr.ai.Finding")),
                  StructTypeReference.create("cel.expr.ai.ToolCall"),
                  SimpleType.STRING
              )
          ),
          newFunctionDeclaration(
              "contains",
              newMemberOverload(
                  "list_finding_contains_list_finding",
                  SimpleType.BOOL,
                  ListType.create(StructTypeReference.create("cel.expr.ai.Finding")),
                  ListType.create(StructTypeReference.create("cel.expr.ai.Finding"))
              )
          )
      )
      .addFunctionBindings(
          CelFunctionBinding.from(
              "ai_finding_string_double",
              ImmutableList.of(String.class, Double.class),
              (args) -> Finding.newBuilder()
                  .setValue((String) args[0])
                  .setConfidence((Double) args[1])
                  .build()
          ),
          CelFunctionBinding.from(
              "agent_message_threats",
              AgentMessage.class,
              (msg) -> {
                if (msg.getPartsCount() > 0 && msg.getParts(0).hasPrompt()) {
                  String content = msg.getParts(0).getPrompt().getContent();
                  if (content.contains("INJECTION_ATTACK")) {
                    return ImmutableList.of(
                        Finding.newBuilder().setValue("prompt_injection").setConfidence(0.95).build()
                    );
                  }
                  if (content.contains("SUSPICIOUS")) {
                    return ImmutableList.of(
                        Finding.newBuilder().setValue("prompt_injection").setConfidence(0.6).build()
                    );
                  }
                }
                return ImmutableList.of();
              }
          ),
          CelFunctionBinding.from(
              "tool_call_sensitivity_label",
              ImmutableList.of(ToolCall.class, String.class),
              (args) -> {
                ToolCall tool = (ToolCall) args[0];
                String label = (String) args[1];
                if ("pii".equals(label) && tool.getName().contains("PII")) {
                  return ImmutableList.of(
                      Finding.newBuilder().setValue("pii").setConfidence(1.0).build()
                  );
                }
                return ImmutableList.of();
              }
          ),
          CelFunctionBinding.from(
              "list_finding_contains_list_finding",
              ImmutableList.of(List.class, List.class),
              (args) -> {
                List<Finding> actualFindings = (List<Finding>) args[0];
                List<Finding> expectedFindings = (List<Finding>) args[1];

                return expectedFindings.stream().anyMatch(expected ->
                    actualFindings.stream().anyMatch(actual ->
                        actual.getValue().equals(expected.getValue()) &&
                            actual.getConfidence() >= expected.getConfidence()
                    )
                );
              }
          )
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
