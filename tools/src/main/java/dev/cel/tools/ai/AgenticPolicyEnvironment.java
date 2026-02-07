package dev.cel.tools.ai;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.bundle.Cel;
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
import dev.cel.expr.ai.AgentMessage;
import dev.cel.expr.ai.AgentMessage.Part;
import dev.cel.expr.ai.ClassificationLabel;
import dev.cel.expr.ai.Finding;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelFunctionBinding;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class AgenticPolicyEnvironment {

  private static final CelOptions CEL_OPTIONS =
      CelOptions.current()
          .enableTimestampEpoch(true)
          .populateMacroCalls(true)
          .build();

  private static final Cel CEL_BASE_ENV =
      CelFactory.standardCelBuilder()
          .setContainer(CelContainer.ofName("cel.expr.ai")) // TODO: config?
          .addFileTypes(Agent.getDescriptor().getFile())
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .setTypeProvider(new AgentTypeProvider())
          .addFunctionBindings(
              CelFunctionBinding.from(
                  "AgentMessage_threatFindings",
                  ImmutableList.of(AgentMessage.class),
                  (args) -> getFindings((AgentMessage) args[0], "threats", ClassificationLabel.Category.THREAT)
              ),
              CelFunctionBinding.from(
                  "ai.finding_string_double",
                  ImmutableList.of(String.class, Double.class),
                  (args) -> Finding.newBuilder()
                      .setValue((String) args[0])
                      .setConfidence((Double) args[1])
                      .build()
              ),
              CelFunctionBinding.from(
                  "optional_type(list(Finding))_hasAll_list(Finding)",
                  ImmutableList.of(Optional.class, List.class),
                  (args) -> hasAllFindings((Optional<List<Finding>>) args[0], (List<Finding>) args[1])
              )
          )
          .setOptions(CEL_OPTIONS)
          .build();

  private static Optional<List<Finding>> getFindings(AgentMessage msg, String labelName, ClassificationLabel.Category category) {
    List<Finding> results = new ArrayList<>();

    for (Part part : msg.getPartsList()) {
      if (part.hasPrompt()) {
        // TODO: Collect from classification
        results.add(Finding.newBuilder().setValue("prompt_injection").setConfidence(1.0d).build());
      } else if (part.hasToolCall()) {
        // TODO: Collect from classification
      }

    }

    if (results.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(results);
  }

  private static boolean hasAllFindings(Optional<List<Finding>> sourceOpt, List<Finding> required) {
    if (!sourceOpt.isPresent()) {
      return false;
    }
    List<Finding> source = sourceOpt.get();

    return required.stream().allMatch(req ->
        source.stream().anyMatch(act ->
            act.getValue().equals(req.getValue()) &&
                act.getConfidence() >= req.getConfidence()
        )
    );
  }

  static Cel newInstance() {
    Cel celEnv = CEL_BASE_ENV;

    celEnv = extendFromConfig(celEnv, "environment/agent_env.yaml");
    celEnv = extendFromConfig(celEnv, "environment/common_env.yaml");
    return extendFromConfig(celEnv, "environment/tool_call_env.yaml");
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

  private AgenticPolicyEnvironment() {}
}
