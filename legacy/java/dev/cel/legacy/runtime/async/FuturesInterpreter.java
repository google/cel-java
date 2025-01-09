package dev.cel.legacy.runtime.async;

import com.google.protobuf.Descriptors.Descriptor;
import dev.cel.common.CelOptions;
import dev.cel.runtime.MessageProvider;
import java.util.Optional;
import java.util.function.Function;

/**
 * Legacy implementation of an optimizing futures-based interpreter for CEL.
 *
 * <p>This is a wrapper around {@link Evaluator}, providing the original interface for backward
 * compatibility.
 */
public class FuturesInterpreter extends Evaluator {

  /** Standard constructor. */
  public FuturesInterpreter(
      TypeResolver typeResolver,
      MessageProcessor messageProcessor,
      AsyncDispatcher dispatcher,
      CelOptions celOptions) {
    super(typeResolver, messageProcessor, dispatcher, celOptions);
  }

  /**
   * Legacy constructor that uses the standard type resolver and adapts a {@link MessageProvider}
   * for use as message processor. Uses legacy features.
   */
  public FuturesInterpreter(
      MessageProvider messageProvider,
      AsyncDispatcher dispatcher,
      Function<String, Optional<Descriptor>> messageLookup) {
    this(messageProvider, dispatcher, messageLookup, CelOptions.LEGACY);
  }

  /**
   * Legacy constructor that uses the standard type resolver and adapts a {@link MessageProvider}
   * for use as message processor. Uses legacy features. Uses provided features.
   */
  public FuturesInterpreter(
      MessageProvider messageProvider,
      AsyncDispatcher dispatcher,
      Function<String, Optional<Descriptor>> messageLookup,
      CelOptions celOptions) {
    super(
        StandardTypeResolver.getInstance(celOptions),
        new MessageProcessorAdapter(messageLookup, messageProvider),
        dispatcher,
        celOptions);
  }
}
