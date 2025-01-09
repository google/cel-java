package dev.cel.legacy.runtime.async;

import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Interface to an object that the {@link Evaluator} can use to resolve calls of Java-coded CEL
 * functions. The primary method {@link FunctionResolver#constructCall} constructs the compiled
 * expression corresponding to a call of the specified function given its compiled arguments.
 *
 * <p>Actual function bindings are usually established (although strictly speaking that is not
 * necessary from the {@link Evaluator}'s point of view) by combining a {@link FunctionResolver}
 * with a corresponding {@link FunctionRegistrar}. The typical use case is codified by {@link
 * AsyncDispatcher}.
 */
public interface FunctionResolver {

  /**
   * Constructs the compliled CEL expression that implements the call of a function at some call
   * site.
   *
   * <p>If multiple overload IDs are given, then a runtime dispatch is implemented. All overload IDs
   * must refer to strict functions in that case.
   *
   * <p>The construction may apply arbitrary optimizations. For example, multiplication with 0 might
   * just generate the constant 0, regardless of the second argument.
   */
  CompiledExpression constructCall(
      @Nullable Metadata metadata,
      long exprId,
      String functionName,
      List<String> overloadIds,
      List<IdentifiedCompiledExpression> compiledArguments,
      MessageProcessor messageProcessor,
      StackOffsetFinder stackOffsetFinder)
      throws InterpreterException;

  /** Determines whether or not the given overload ID corresponds to a known function binding. */
  boolean isBound(String overloadId);
}
