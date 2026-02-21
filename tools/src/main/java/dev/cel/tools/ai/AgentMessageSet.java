package dev.cel.tools.ai;

import com.google.auto.value.AutoValue;
import com.google.protobuf.Timestamp;
import dev.cel.expr.ai.AgentContext;
import dev.cel.expr.ai.AgentContextExtensions;
import dev.cel.expr.ai.AgentMessage;
import dev.cel.expr.ai.AgentMessage.Part;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AgentMessageSet value which represents a filtered set of agent messages.
 */
@AutoValue
abstract class AgentMessageSet {

  /**
   * Underlying {@link AgentContext} containing the message history.
   */
  abstract AgentContext context();

  /** Returns the role to filter by, if present. */
  abstract Optional<String> role();

  /** Returns the tool call name to filter by, if present. */
  abstract Optional<String> toolCallName();

  /** Returns the result type (MIME type) to filter by, if present. */
  abstract Optional<String> resultType();

  /**
   * Returns the exclusive upper bound timestamp for filtering messages, if
   * present.
   */
  abstract Optional<Instant> before();

  /**
   * Returns the exclusive lower bound timestamp for filtering messages, if
   * present.
   */
  abstract Optional<Instant> after();

  /** Returns true if only keys (prompts) should be included, false otherwise. */
  abstract boolean prompts();

  /**
   * Creates a new {@link AgentMessageSet} from the given {@link AgentContext}.
   */
  static AgentMessageSet of(AgentContext context) {
    return builder().setContext(context).setPrompts(false).build();
  }

  /**
   * Creates a new {@link AgentMessageSet} containing a single
   * {@link AgentMessage}.
   *
   * <p>
   * This convenience method wraps the message in a new {@link AgentContext}.
   */
  static AgentMessageSet of(AgentMessage message) {
    AgentContext.Builder contextBuilder = AgentContext.newBuilder();
    contextBuilder.addExtension(AgentContextExtensions.agentContextMessageHistory, message);
    return of(contextBuilder.build());
  }

  /** Returns a new {@link Builder} for {@link AgentMessageSet}. */
  static Builder builder() {
    return new AutoValue_AgentMessageSet.Builder();
  }

  /**
   * Returns a new {@link Builder} initialized with the values of this instance.
   */
  abstract Builder toBuilder();

  /** Builder for {@link AgentMessageSet}. */
  @AutoValue.Builder
  abstract static class Builder {
    /** Sets the {@link AgentContext}. */
    abstract Builder setContext(AgentContext context);

    /** Sets the role filter. */
    abstract Builder setRole(String role);

    /** Sets the tool call name filter. */
    abstract Builder setToolCallName(String toolCallName);

    /** Sets the result type filter. */
    abstract Builder setResultType(String resultType);

    /** Sets the before timestamp filter. */
    abstract Builder setBefore(Instant before);

    /** Sets the after timestamp filter. */
    abstract Builder setAfter(Instant after);

    /** Sets whether to include prompts only. */
    abstract Builder setPrompts(boolean prompts);

    /** Builds the {@link AgentMessageSet}. */
    abstract AgentMessageSet build();
  }

  /**
   * Returns the filtered messages as an {@link AgentContext} proto.
   *
   * <p>
   * This method applies all configured filters (role, time, tool call, etc.) to
   * the messages in
   * the underlying context and returns a new {@link AgentContext} with the
   * filtered history.
   */
  AgentContext filteredContext() {
    List<AgentMessage> msgs = context().getExtension(AgentContextExtensions.agentContextMessageHistory);
    if (msgs.isEmpty()) {
      return context();
    }
    List<AgentMessage> filteredMsgs = new ArrayList<>();

    for (AgentMessage msg : msgs) {
      if (role().isPresent() && !msg.getRole().equals(role().get())) {
        continue;
      }
      Timestamp msgTime = msg.getTime();
      Instant time = Instant.ofEpochSecond(msgTime.getSeconds(), msgTime.getNanos());

      if (after().isPresent() && time.isBefore(after().get())) {
        continue;
      }
      if (before().isPresent() && time.isAfter(before().get())) {
        continue;
      }

      List<Part> filteredParts = new ArrayList<>();
      for (Part part : msg.getPartsList()) {
        if (prompts() && !part.hasPrompt()) {
          continue;
        }
        if (toolCallName().isPresent()) {
          if (!part.hasToolCall()) {
            continue;
          }
          if (!part.getToolCall().getName().equals(toolCallName().get())) {
            continue;
          }
        }
        if (resultType().isPresent()) {
          if (part.hasToolCall() && part.getToolCall().hasResult()) {
            if (part.getToolCall().getResult().getMimeType().equals(resultType().get())) {
              filteredParts.add(part);
            }
          } else if (part.hasAttachment()) {
            if (part.getAttachment().getMimeType().equals(resultType().get())) {
              filteredParts.add(part);
            }
          }
          continue;
        }
        filteredParts.add(part);
      }

      filteredMsgs.add(msg.toBuilder().clearParts().addAllParts(filteredParts).build());
    }

    return context().toBuilder()
        .setExtension(AgentContextExtensions.agentContextMessageHistory, filteredMsgs)
        .build();
  }

  /** Returns a new {@link AgentMessageSet} filtered by the given role. */
  AgentMessageSet filterRole(String role) {
    return toBuilder().setRole(role).build();
  }

  /**
   * Returns a new {@link AgentMessageSet} filtered by the given tool call name.
   */
  AgentMessageSet filterToolCall(String toolCallName) {
    return toBuilder().setToolCallName(toolCallName).build();
  }

  /**
   * Returns a new {@link AgentMessageSet} filtered by the given result type (MIME
   * type).
   */
  AgentMessageSet filterResultType(String resultType) {
    return toBuilder().setResultType(resultType).build();
  }

  /**
   * Returns a new {@link AgentMessageSet} filtered to include messages before the
   * given timestamp.
   */
  AgentMessageSet filterBefore(Instant timestamp) {
    return toBuilder()
        .setBefore(timestamp)
        .build();
  }

  /**
   * Returns a new {@link AgentMessageSet} filtered to include messages after the
   * given timestamp.
   */
  AgentMessageSet filterAfter(Instant timestamp) {
    return toBuilder()
        .setAfter(timestamp)
        .build();
  }

  /**
   * Returns a new {@link AgentMessageSet} filtered to include only prompts (keys)
   * if true.
   */
  AgentMessageSet filterPrompts(boolean prompts) {
    return toBuilder().setPrompts(prompts).build();
  }
}
