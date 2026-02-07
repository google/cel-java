package dev.cel.tools.ai;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelEnvironment;
import dev.cel.bundle.CelEnvironmentException;
import dev.cel.bundle.CelEnvironmentYamlParser;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelContainer;
import dev.cel.common.CelOptions;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.OpaqueType;
import dev.cel.expr.ai.Agent;
import dev.cel.expr.ai.AgentContext;
import dev.cel.expr.ai.AgentContextExtensions;
import dev.cel.expr.ai.AgentMessage;
import dev.cel.expr.ai.Finding;
import dev.cel.expr.ai.ToolCall;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelFunctionBinding;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

final class AgenticPolicyEnvironment {

  private static final CelOptions CEL_OPTIONS = CelOptions.current()
      .enableTimestampEpoch(true)
      .populateMacroCalls(true)
      .build();

  @SuppressWarnings("Immutable")
  static Cel newInstance(AgentClassifier classifier) {
    AgenticPolicyClassifiers classifiers = new AgenticPolicyClassifiers(classifier);
    CelBuilder builder = CelFactory.standardCelBuilder()
        .setContainer(CelContainer.ofName("cel.expr.ai"))
        .addFileTypes(Agent.getDescriptor().getFile())
        .addFileTypes(AgentContextExtensions.getDescriptor().getFile())
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .setTypeProvider(new AgentTypeProvider())
        .setOptions(CEL_OPTIONS);

    builder.addFunctionBindings(
        CelFunctionBinding.from(
            "AgentMessage_threatFindings",
            AgentMessage.class,
            classifiers::threatFindings),
        CelFunctionBinding.from(
            "AgentContext_threatFindings",
            AgentContext.class,
            classifiers::threatFindings),
        CelFunctionBinding.from(
            "ToolCall_threatFindings",
            ToolCall.class,
            classifiers::threatFindings),
        CelFunctionBinding.from(
            "AgentContext_safetyFindings_string",
            AgentContext.class,
            String.class,
            classifiers::safetyFindings),
        CelFunctionBinding.from(
            "AgentMessage_safetyFindings_string",
            AgentMessage.class,
            String.class,
            classifiers::safetyFindings),
        CelFunctionBinding.from(
            "ToolCall_safetyFindings_string",
            ToolCall.class,
            String.class,
            classifiers::safetyFindings),
        CelFunctionBinding.from(
            "AgentContext_sensitivityFindings_string",
            AgentContext.class,
            String.class,
            classifiers::sensitivityFindings),
        CelFunctionBinding.from(
            "AgentMessage_sensitivityFindings_string",
            AgentMessage.class,
            String.class,
            classifiers::sensitivityFindings),
        CelFunctionBinding.from(
            "ToolCall_sensitivityFindings_string",
            ToolCall.class,
            String.class,
            classifiers::sensitivityFindings),
        CelFunctionBinding.from(
            "ai.finding_string_double",
            String.class,
            Double.class,
            (value, confidence) -> Finding.newBuilder()
                .setValue(value)
                .setConfidence(confidence)
                .build()),
        CelFunctionBinding.from(
            "optional_type(list(Finding))_hasAll_list(Finding)",
            Optional.class,
            List.class,
            (opt, required) -> hasAllFindings((Optional<List<Finding>>) opt, (List<Finding>) required)),
        CelFunctionBinding.from(
            "AgentMessage_toolCalls_string",
            AgentMessage.class,
            String.class,
            (msg, toolName) -> AgentMessageSet.of(msg).filterToolCall(toolName)),
        CelFunctionBinding.from(
            "AgentMessage_role_string",
            AgentMessage.class,
            String.class,
            (msg, role) -> AgentMessageSet.of(msg).filterRole(role)));

    Cel celEnv = builder.build();
    celEnv = extendFromConfig(celEnv, "environment/agent_env.yaml");
    celEnv = extendFromConfig(celEnv, "environment/common_env.yaml");
    return extendFromConfig(celEnv, "environment/tool_call_env.yaml");
  }

  private static boolean hasAllFindings(Optional<List<Finding>> sourceOpt, List<Finding> required) {
    if (!sourceOpt.isPresent()) {
      return false;
    }
    List<Finding> source = sourceOpt.get();

    return required.stream().allMatch(req -> source.stream().anyMatch(act -> act.getValue().equals(req.getValue()) &&
        act.getConfidence() >= req.getConfidence()));
  }

  static Cel newInstance() {
    return newInstance(AgentClassifier.DEFAULT);
  }

  private static Cel extendFromConfig(Cel cel, String yamlConfigPath) {
    String yamlEnv;
    try {
      yamlEnv = readFile(yamlConfigPath);
    } catch (IOException e) {
      String errorMsg = String.format("Failed to read %s: %s", yamlConfigPath, e.getMessage());
      throw new IllegalArgumentException(errorMsg, e);
    }
    try {
      CelEnvironment env = CelEnvironmentYamlParser.newInstance().parse(yamlEnv);
      return env.extend(cel, CEL_OPTIONS);
    } catch (CelEnvironmentException e) {
      String errorMsg = String.format("Failed to extend CEL environment from %s: %s", yamlConfigPath, e.getMessage());
      throw new IllegalArgumentException(errorMsg, e);
    }
  }

  private static String readFile(String path) throws IOException {
    URL url = Resources.getResource(Ascii.toLowerCase(path));
    return Resources.toString(url, UTF_8);
  }

  private static final class AgentTypeProvider implements CelTypeProvider {
    private static final OpaqueType AGENT_MESSAGE_SET_TYPE = OpaqueType.create("cel.expr.ai.AgentMessageSet");

    private static final ImmutableSet<CelType> ALL_TYPES = ImmutableSet.of(AGENT_MESSAGE_SET_TYPE);

    @Override
    public ImmutableCollection<CelType> types() {
      return ALL_TYPES;
    }

    @Override
    public Optional<CelType> findType(String typeName) {
      if (typeName.equals(AGENT_MESSAGE_SET_TYPE.name())) {
        return Optional.of(AGENT_MESSAGE_SET_TYPE);
      }

      return Optional.empty();
    }
  }

  private AgenticPolicyEnvironment() {
  }
}
