package dev.cel.legacy.runtime.async;

/**
 * Interface to an object that combines a {@link FunctionRegistrar} with a corresponding {@link
 * FunctionResolver}.
 */
public interface AsyncDispatcher extends FunctionRegistrar, FunctionResolver {

  /**
   * Creates an independent copy of the current state of the dispatcher. Further updates to either
   * the original or the forked copy do not affect the respective other.
   */
  AsyncDispatcher fork();
}
