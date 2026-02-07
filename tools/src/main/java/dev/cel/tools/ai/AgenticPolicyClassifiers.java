package dev.cel.tools.ai;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessage.GeneratedExtension;
import dev.cel.expr.ai.AgentContext;
import dev.cel.expr.ai.AgentContextExtensions;
import dev.cel.expr.ai.AgentMessage;
import dev.cel.expr.ai.AgentMessage.Part;
import dev.cel.expr.ai.ClassificationLabel;
import dev.cel.expr.ai.ClassificationLabel.Category;
import dev.cel.expr.ai.Finding;
import dev.cel.expr.ai.ToolCall;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helper class for extracting classification findings from Agent components.
 */
public final class AgenticPolicyClassifiers {

    private final AgentClassifier classifier;

    public AgenticPolicyClassifiers(AgentClassifier classifier) {
        this.classifier = classifier;
    }

    public Optional<List<Finding>> threatFindings(Object input) {
        return collectFindings(input, "*", Category.THREAT);
    }

    public Optional<List<Finding>> safetyFindings(Object input, String label) {
        return collectFindings(input, label, Category.SAFETY);
    }

    public Optional<List<Finding>> sensitivityFindings(Object input, String label) {
        return collectFindings(input, label, Category.SENSITIVITY);
    }

    private Optional<List<Finding>> collectFindings(
            Object input, String label, Category category) {
        FindingAggregator aggregator = new FindingAggregator();
        aggregator.collect(input, label);

        // Collect from external classifier
        classifier.classify(input, label).ifPresent(findings -> {
            aggregator.addExternalFindings(findings, category);
        });

        List<Finding> findings = aggregator.getFindings();
        if (findings.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(findings);
    }

    private static class FindingAggregator {
        private final List<ClassificationLabel> labels = new ArrayList<>();
        private final List<Finding> externalFindings = new ArrayList<>();

        void addExternalFindings(List<Finding> findings, Category category) {
            externalFindings.addAll(findings);
        }

        void collect(Object input, String labelName) {
            if (input instanceof AgentContext) {
                collectContext((AgentContext) input, labelName);
            } else if (input instanceof AgentMessage) {
                collectMessage((AgentMessage) input, labelName);
            } else if (input instanceof AgentMessageSet) {
                collectContext(((AgentMessageSet) input).filteredContext(), labelName);
            } else if (input instanceof ToolCall) {
                collectToolCall((ToolCall) input, labelName);
            }
        }

        private void collectContext(AgentContext ctx, String labelName) {
            collectExt(ctx, AgentContextExtensions.agentContextClassificationLabels, labelName);
            if (ctx.hasExtension(AgentContextExtensions.agentContextMessageHistory)) {
                for (AgentMessage msg : ctx.getExtension(AgentContextExtensions.agentContextMessageHistory)) {
                    collectMessage(msg, labelName);
                }
            }
        }

        private void collectMessage(AgentMessage msg, String labelName) {
            for (Part part : msg.getPartsList()) {
                if (part.hasPrompt()) {
                    collectExt(
                        part.getPrompt(), AgentContextExtensions.contentClassificationLabels, labelName);
                } else if (part.hasToolCall()) {
                    collectToolCall(part.getToolCall(), labelName);
                } else if (part.hasAttachment()) {
                    collectExt(
                        part.getAttachment(),
                        AgentContextExtensions.contentClassificationLabels,
                        labelName);
                }
            }
        }

        private void collectToolCall(ToolCall call, String labelName) {
            collectExt(call, AgentContextExtensions.toolCallClassificationLabels, labelName);
            if (call.hasResult()) {
                collectExt(
                    call.getResult(), AgentContextExtensions.contentClassificationLabels, labelName);
            }
        }

        private <T extends GeneratedMessage.ExtendableMessage<T>> void collectExt(
            T message, GeneratedExtension<T, List<ClassificationLabel>> extension, String labelName) {
            List<ClassificationLabel> extLabels = message.getExtension(extension);
            if (extLabels == null || extLabels.isEmpty()) {
                return;
            }
            if (labelName.equals("*")) {
                labels.addAll(extLabels);
                return;
            }
            for (ClassificationLabel lbl : extLabels) {
                if (lbl.getName().equals(labelName)) {
                    labels.add(lbl);
                }
            }
        }

        List<Finding> getFindings() {
            Map<String, List<Finding>> findingsByLabel = new HashMap<>();
            for (ClassificationLabel lbl : labels) {
                findingsByLabel.computeIfAbsent(lbl.getName(), k -> new ArrayList<>())
                    .addAll(lbl.getFindingsList());
            }

            List<Finding> allFindings = new ArrayList<>();
            for (List<Finding> lblFindings : findingsByLabel.values()) {
                allFindings.addAll(unionFindings(lblFindings));
            }
            if (!externalFindings.isEmpty()) {
                allFindings.addAll(unionFindings(externalFindings));
            }
            return allFindings;
        }

        private List<Finding> unionFindings(List<Finding> findings) {
            Map<String, List<Finding>> findingsByValue = new HashMap<>();
            for (Finding f : findings) {
                findingsByValue.computeIfAbsent(f.getValue(), k -> new ArrayList<>()).add(f);
            }

            List<Finding> result = new ArrayList<>();
            for (List<Finding> group : findingsByValue.values()) {
                result.add(unionFindingsForValue(group));
            }
            result.sort(Comparator.comparing(Finding::getValue));
            return result;
        }

        private Finding unionFindingsForValue(List<Finding> findings) {
            Finding best = findings.get(0);
            for (int i = 1; i < findings.size(); i++) {
                Finding current = findings.get(i);
                if (current.getConfidence() > best.getConfidence()) {
                    best = current;
                }
            }
            return best;
        }
    }
}
