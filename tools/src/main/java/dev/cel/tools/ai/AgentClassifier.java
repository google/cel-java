package dev.cel.tools.ai;

import dev.cel.expr.ai.Finding;
import java.util.List;
import java.util.Optional;

/**
 * Interface for providing content classifiers.
 */
public interface AgentClassifier {
    /**
     * Classifies the given input and returns a list of findings.
     *
     * @param input the input object (e.g., AgentContext, AgentMessage, ToolCall)
     * @param label the classification label to match (or "*" for all)
     */
    Optional<List<Finding>> classify(Object input, String label);

    /** A default classifier that returns no findings. */
    AgentClassifier DEFAULT = (input, label) -> Optional.empty();
}
