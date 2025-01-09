package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import dev.cel.runtime.Registrar;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Interface to an object that accepts bindings of Java implementations to CEL functions that can be
 * used by the {@link Evaluator} via a corresponding {@link FunctionResolver}.
 *
 * <p>A class implementing this interface must define {@link FunctionRegistrar#addCallConstructor}
 * and {@link FunctionRegistrar#addStrictFunction}. All other methods already have a default
 * implementation in terms of these.
 */
public interface FunctionRegistrar extends Registrar {

  /** Constructs a {@link CompiledExpression} for the given call. */
  @Immutable
  @FunctionalInterface
  interface CallConstructor {
    CompiledExpression construct(
        @Nullable Metadata metadata,
        long exprId,
        List<IdentifiedCompiledExpression> compiledArguments,
        MessageProcessor messageProcessor,
        StackOffsetFinder stackOffsetFinder)
        throws InterpreterException;
  }

  /**
   * Simplified version of {@link CallConstructor} without the message processor and without the
   * stack offset finder.
   */
  @Immutable
  @FunctionalInterface
  interface SimpleCallConstructor {
    CompiledExpression construct(
        @Nullable Metadata metadata,
        long exprId,
        List<IdentifiedCompiledExpression> compiledArguments)
        throws InterpreterException;
  }

  /** Simplified version of {@link CallConstructor} without the stack offset finder. */
  @Immutable
  @FunctionalInterface
  interface SimpleCallConstructorWithStackAccess {
    CompiledExpression construct(
        @Nullable Metadata metadata,
        long exprId,
        List<IdentifiedCompiledExpression> compiledArguments,
        StackOffsetFinder stackOffsetFinder)
        throws InterpreterException;
  }

  /**
   * A function bound to one out of several possible overloads of a CEL function.
   *
   * <p>Overloadable functions have to be strict (i.e., their arguments get fully evaluated before
   * they are called). This is a necessary prerequisite for runtime overload resolution, but it also
   * opens the possibility of memoization.
   */
  @Immutable
  @FunctionalInterface
  interface StrictFunction {
    ListenableFuture<Object> apply(GlobalContext globalContext, List<Object> arguments);
  }

  // Core functionality.
  //
  // All convenience methods below are imlpemented in terms of just
  // addCallConstructor and addOverloadableFunction.

  /**
   * Adds a generic call constructor for the given overload ID. Function calls with this overload ID
   * will later be handled by the given {@link CallConstructor}. No runtime overloading is possible.
   */
  void addCallConstructor(String overloadId, CallConstructor callConstructor);

  /** Adds a simple generic call constructor. */
  default void addCallConstructor(String overloadId, SimpleCallConstructor simpleCallConstructor) {
    addCallConstructor(
        overloadId,
        (md, id, args, ignoredMessageProcessor, ignoredStackOffsetFinder) ->
            simpleCallConstructor.construct(md, id, args));
  }

  /** Adds a simple generic call constructor with stack access. */
  default void addCallConstructor(
      String overloadId,
      SimpleCallConstructorWithStackAccess simpleCallConstructorWithStackAccess) {
    addCallConstructor(
        overloadId,
        (md, id, args, ignoredMessageProcessor, stackOffsetFinder) ->
            simpleCallConstructorWithStackAccess.construct(md, id, args, stackOffsetFinder));
  }

  /** Registers one possible binding for a runtime-overloadable function. */
  void addStrictFunction(
      String overloadId,
      List<Class<?>> argumentTypes,
      boolean contextIndependent,
      StrictFunction strictFunction);

  // Convenience methods with default implementations.
  // A class implementing interface {@link FunctionRegistry} should not provide
  // its own implementations.

  /** Interface to type unary asynchronous function. */
  @Immutable
  @FunctionalInterface
  interface StrictUnaryFunction<T> {
    ListenableFuture<Object> apply(T arg);
  }

  /** Interface to typed binary asynchronous function with barrier synchronization. */
  @Immutable
  @FunctionalInterface
  interface StrictBinaryFunction<T1, T2> {
    ListenableFuture<Object> apply(T1 arg1, T2 arg2);
  }

  /**
   * Interface to a general asynchronous function that operates without implicit barrier
   * synchronization on argument evaluation. All arguments are being evaluated, though. These
   * functions are also non-strict in the sense that an error in an unused argument will not lead to
   * an error in the function application itself.
   */
  @Immutable
  @FunctionalInterface
  interface NobarrierFunction {
    ListenableFuture<Object> apply(
        GlobalContext globalContext, List<ListenableFuture<Object>> args);
  }

  /**
   * Registers general asynchronous overloadable function.
   *
   * <p>Caution: The function's continuation will run on its returned future's executor. Make sure
   * to only use this registration method if such an arrangement is safe. If the future's executor
   * (which might, for example, be an RPC event manager's executor) is not appropriate, then prefer
   * using {@link #addAsync}.
   */
  default void addDirect(
      String overloadId, List<Class<?>> argTypes, Effect effect, StrictFunction function) {
    addStrictFunction(overloadId, argTypes, effect.equals(Effect.CONTEXT_INDEPENDENT), function);
  }

  /**
   * Registers a general asynchronous {@link StrictFunction} that takes a variable number of
   * arguments.
   *
   * <p>Caveat: This function cannot participate in runtime overload resolution.
   *
   * <p>Caution: The function's continuation will run on its returned future's executor. Make sure
   * to only use this registration method if such an arrangement is safe. If the future's executor
   * (which might, for example, be an RPC event manager's executor) is not appropriate, then prefer
   * using {@link #addAsync}.
   */
  default void addDirect(String overloadId, Effect effect, StrictFunction function) {
    addCallConstructor(
        overloadId,
        (metadata, exprId, compiledArguments) ->
            EvaluationHelpers.compileStrictCall(function, overloadId, effect, compiledArguments));
  }

  /**
   * Registers typed unary asynchronous function. The function's continuation will use the returned
   * future's executor.
   */
  default <T> void addDirect(
      String overloadId, Class<T> argType, Effect effect, StrictUnaryFunction<T> function) {
    addDirect(
        overloadId,
        ImmutableList.of(argType),
        effect,
        (gctx, args) -> function.apply(argType.cast(args.get(0))));
  }

  /**
   * Registers typed binary asynchronous function. The function's continuation will use the returned
   * future's executor.
   */
  default <T1, T2> void addDirect(
      String overloadId,
      Class<T1> argType1,
      Class<T2> argType2,
      Effect effect,
      StrictBinaryFunction<T1, T2> function) {
    addDirect(
        overloadId,
        ImmutableList.of(argType1, argType2),
        effect,
        (gctx, args) -> function.apply(argType1.cast(args.get(0)), argType2.cast(args.get(1))));
  }

  /**
   * Registers an executor-coupling version of the given asynchronous {@link StrictFunction}.
   * Coupling guarantees that transformations on the result run on the context's executor even if
   * they specify {@code MoreExecutors#directExecutor}.
   */
  default void addAsync(
      String overloadId, List<Class<?>> argTypes, Effect effect, StrictFunction function) {
    addDirect(
        overloadId,
        argTypes,
        effect,
        (gctx, args) ->
            gctx.context().coupleToExecutorInRequestContext(() -> function.apply(gctx, args)));
  }

  /** Registers variadic asynchronous {@link StrictFunction} in executor-coupling fashion. */
  default void addAsync(String overloadId, Effect effect, StrictFunction function) {
    addDirect(
        overloadId,
        effect,
        (gctx, args) ->
            gctx.context().coupleToExecutorInRequestContext(() -> function.apply(gctx, args)));
  }

  /** Registers typed unary asynchronous function in executor-coupling fashion. */
  default <T> void addAsync(
      String overloadId, Class<T> argType, Effect effect, StrictUnaryFunction<T> function) {
    addDirect(
        overloadId,
        ImmutableList.of(argType),
        effect,
        (gctx, args) ->
            gctx.context()
                .coupleToExecutorInRequestContext(() -> function.apply(argType.cast(args.get(0)))));
  }

  /** Registers typed binary asynchronous function in executor-coupling fashion. */
  default <T1, T2> void addAsync(
      String overloadId,
      Class<T1> argType1,
      Class<T2> argType2,
      Effect effect,
      StrictBinaryFunction<T1, T2> function) {
    addDirect(
        overloadId,
        ImmutableList.of(argType1, argType2),
        effect,
        (gctx, args) ->
            gctx.context()
                .coupleToExecutorInRequestContext(
                    () -> function.apply(argType1.cast(args.get(0)), argType2.cast(args.get(1)))));
  }

  /** Registers a no-barrier asynchronous function. */
  default void addNobarrierAsync(String overloadId, Effect effect, NobarrierFunction function) {
    addCallConstructor(
        overloadId,
        (metadata, exprId, compiledArguments) ->
            EvaluationHelpers.compileNobarrierCall(function, effect, compiledArguments));
  }

  // Registrar methods

  /** Adds a unary function to the dispatcher. */
  @Override
  default <T> void add(String overloadId, Class<T> argType, UnaryFunction<T> function) {
    addDirect(
        overloadId,
        argType,
        Effect.CONTEXT_INDEPENDENT,
        arg -> {
          try {
            return immediateFuture(function.apply(arg));
          } catch (RuntimeException e) {
            return immediateFailedFuture(
                new InterpreterException.Builder(
                        e, "Function '%s' failed with arg(s) '%s'", overloadId, arg)
                    .build());
          } catch (Exception e) {
            return immediateFailedFuture(InterpreterException.wrapOrThrow(e));
          }
        });
  }

  /** Adds a binary function to the dispatcher. */
  @Override
  default <T1, T2> void add(
      String overloadId, Class<T1> argType1, Class<T2> argType2, BinaryFunction<T1, T2> function) {
    addDirect(
        overloadId,
        argType1,
        argType2,
        Effect.CONTEXT_INDEPENDENT,
        (arg1, arg2) -> {
          try {
            return immediateFuture(function.apply(arg1, arg2));
          } catch (RuntimeException e) {
            return immediateFailedFuture(
                new InterpreterException.Builder(
                        e,
                        "Function '%s' failed with arg(s) '%s'",
                        overloadId,
                        Joiner.on(", ").join(Arrays.asList(arg1, arg2)))
                    .build());
          } catch (Exception e) {
            return immediateFailedFuture(InterpreterException.wrapOrThrow(e));
          }
        });
  }

  /** Adds a general function to the dispatcher. */
  @Override
  default void add(String overloadId, List<Class<?>> argTypes, Function function) {
    addDirect(
        overloadId,
        argTypes,
        Effect.CONTEXT_INDEPENDENT,
        (gctx, args) -> {
          try {
            return immediateFuture(function.apply(args.toArray()));
          } catch (RuntimeException e) {
            return immediateFailedFuture(
                new InterpreterException.Builder(
                        e,
                        "Function '%s' failed with arg(s) '%s'",
                        overloadId,
                        Joiner.on(", ").join(args))
                    .build());
          } catch (Exception e) {
            return immediateFailedFuture(InterpreterException.wrapOrThrow(e));
          }
        });
  }
}
