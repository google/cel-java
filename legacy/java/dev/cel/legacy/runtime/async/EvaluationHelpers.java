package dev.cel.legacy.runtime.async;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dev.cel.common.CelErrorCode;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A collection of helper methods. These provide the implementations of all default (convenience)
 * methods in {@link FunctionResolver} and can also be used in custom registrars and resolvers.
 */
public final class EvaluationHelpers {

  /**
   * Applies constant folding of a context-independent strict function when all its arguments are
   * constants themselves.
   */
  private static CompiledExpression constantfoldStrictCall(
      FunctionRegistrar.StrictFunction function, List<CompiledExpression> compiledArguments) {
    return compiledConstantOrThrowing(
        () ->
            Futures.getDone(
                function.apply(
                    COMPILE_TIME_GLOBAL_CONTEXT, transform(compiledArguments, e -> e.constant()))));
  }

  /**
   * Constructs the call of a strict function. Context-independent functions are simply applied,
   * while context-dependent function calls are memoized using the {@link AsyncContext#perform}
   * method.
   */
  // This lambda implements @Immutable interface 'ExecutableExpression', but 'List' is mutable
  @SuppressWarnings("Immutable")
  private static CompiledExpression constructStrictCall(
      FunctionRegistrar.StrictFunction function,
      String memoizationKey,
      Effect functionEffect,
      List<CompiledExpression> compiledArguments) {
    List<ExecutableExpression> executableArguments = new ArrayList<>();
    Effect effect = functionEffect;
    for (CompiledExpression argument : compiledArguments) {
      if (argument.isThrowing()) {
        return argument;
      }
      executableArguments.add(argument.toExecutable());
      effect = effect.meet(argument.effect());
    }
    if (functionEffect.equals(Effect.CONTEXT_INDEPENDENT)) {
      return CompiledExpression.executable(
          stack ->
              execAllAsList(executableArguments, stack)
                  .transformAsync(
                      arguments -> function.apply(stack.globalContext(), arguments),
                      directExecutor()),
          effect);
    }
    return CompiledExpression.executable(
        stack ->
            execAllAsList(executableArguments, stack)
                .transformAsync(
                    arguments ->
                        stack
                            .currentContext()
                            .perform(
                                () -> function.apply(stack.globalContext(), arguments),
                                memoizationKey,
                                arguments),
                    directExecutor()),
        effect);
  }

  /**
   * Compiles the call of an overloadable function by either constant-folding it or by constructing
   * the residual runtime call.
   */
  public static CompiledExpression compileStrictCall(
      FunctionRegistrar.StrictFunction function,
      String memoizationKey,
      Effect functionEffect,
      List<IdentifiedCompiledExpression> identifiedCompiledArguments)
      throws InterpreterException {
    List<CompiledExpression> compiledArguments = new ArrayList<>();
    boolean allConstant = true;
    for (var ca : identifiedCompiledArguments) {
      CompiledExpression e = ca.expression();
      compiledArguments.add(e);
      if (!e.isConstant()) {
        allConstant = false;
      }
    }
    return functionEffect.equals(Effect.CONTEXT_INDEPENDENT) && allConstant
        ? constantfoldStrictCall(function, compiledArguments)
        : constructStrictCall(function, memoizationKey, functionEffect, compiledArguments);
  }

  /**
   * Attempts to constant-fold a call of a context-independent "no-barrier" function. Returns an
   * empty result if the call needed the value of one of its non-constant arguments.
   */
  private static CompiledExpression constantfoldNobarrierCall(
      FunctionRegistrar.NobarrierFunction function, List<CompiledExpression> compiledArguments) {
    return compiledConstantOrThrowing(
        () ->
            Futures.getDone(
                function.apply(
                    COMPILE_TIME_GLOBAL_CONTEXT,
                    transform(compiledArguments, e -> getStaticArgumentFuture(e)))));
  }

  /** Constructs the call of a "no-barrier" function. */
  // This lambda implements @Immutable interface 'ExecutableExpression', but 'List' is mutable
  @SuppressWarnings("Immutable")
  private static CompiledExpression constructNobarrierCall(
      FunctionRegistrar.NobarrierFunction function,
      Effect functionEffect,
      List<CompiledExpression> compiledArguments) {
    Effect effect = functionEffect;
    List<ExecutableExpression> executableArguments = new ArrayList<>();
    for (CompiledExpression argument : compiledArguments) {
      executableArguments.add(argument.toExecutable());
      effect = effect.meet(argument.effect());
    }
    return CompiledExpression.executable(
        stack ->
            FluentFuture.from(
                function.apply(
                    stack.globalContext(),
                    transform(executableArguments, arg -> arg.execute(stack)))),
        effect);
  }

  /**
   * Compiles the call of a "no-barrier" function by either constant-folding it or by constructing
   * the residual runtime call.
   */
  public static CompiledExpression compileNobarrierCall(
      FunctionRegistrar.NobarrierFunction function,
      Effect functionEffect,
      List<IdentifiedCompiledExpression> identifiedCompiledArguments)
      throws InterpreterException {
    List<CompiledExpression> compiledArguments = new ArrayList<>();
    boolean allStatic = true;
    for (var ia : identifiedCompiledArguments) {
      CompiledExpression e = ia.expression();
      compiledArguments.add(e);
      if (e.isExecutable()) {
        allStatic = false;
      }
    }
    return functionEffect.equals(Effect.CONTEXT_INDEPENDENT) && allStatic
        ? constantfoldNobarrierCall(function, compiledArguments)
        : constructNobarrierCall(function, functionEffect, compiledArguments);
  }

  /** Implements immediateFuture for {@link FluentFuture}s. */
  public static <A> FluentFuture<A> immediateValue(A a) {
    return FluentFuture.from(immediateFuture(a));
  }

  /** Implements immediateFailedFuture for {@link FluentFuture}s. */
  public static <A> FluentFuture<A> immediateException(Throwable t) {
    return FluentFuture.from(immediateFailedFuture(t));
  }

  /**
   * Transforms a list. Unlike {@link Lists#transform} This does not produce a transformed "view" of
   * the original. Instead, the result is stored in an immutable list and does not update itself
   * should the input get updated later.
   */
  public static <A, B> ImmutableList<B> transform(List<A> input, Function<? super A, B> function) {
    return input.stream().map(function).collect(toImmutableList());
  }

  /**
   * Turns the given list of futures into a future of a list. If at least one of the futures fails,
   * the result future fails with the exception of the earliest (lowes-index) failing input future.
   */
  public static <V> ListenableFuture<ImmutableList<V>> allAsListOrFirstException(
      Iterable<? extends ListenableFuture<? extends V>> futures) {
    return buildListOrFirstException(futures.iterator(), ImmutableList.builder());
  }

  /** Helper for {@link #allAsListOrFirstException}. */
  private static <V> ListenableFuture<ImmutableList<V>> buildListOrFirstException(
      Iterator<? extends ListenableFuture<? extends V>> futures, ImmutableList.Builder<V> builder) {
    if (!futures.hasNext()) {
      return immediateFuture(builder.build());
    }
    return FluentFuture.from(futures.next())
        .transformAsync(v -> buildListOrFirstException(futures, builder.add(v)), directExecutor());
  }

  /** Executes all expressions and arranges for the results to be combined into a single list. */
  public static FluentFuture<ImmutableList<Object>> execAllAsList(
      List<ExecutableExpression> executables, DynamicEnv stack) {
    return FluentFuture.from(
        allAsListOrFirstException(transform(executables, exp -> exp.execute(stack))));
  }

  /**
   * Creates a constant compiled expression from the supplied constant. If the supplier throws an
   * exception, a throwing expression is created instead.
   */
  public static CompiledExpression compiledConstantOrThrowing(Callable<Object> supplier) {
    try {
      return CompiledExpression.constant(supplier.call());
    } catch (ExecutionException execExn) {
      return CompiledExpression.throwing(execExn.getCause());
    } catch (Exception exn) {
      return CompiledExpression.throwing(exn);
    }
  }

  /** Enforces that the given value is a boolean. Throws an explanatory exception otherwise. */
  public static Object expectBoolean(Object b, Metadata metadata, long id)
      throws InterpreterException {
    if (b instanceof Boolean) {
      return b;
    }
    throw new InterpreterException.Builder("expected boolean value, found: %s", b)
        .setErrorCode(CelErrorCode.INVALID_ARGUMENT)
        .setLocation(metadata, id)
        .build();
  }

  /** Extracts a boolean value. */
  public static boolean asBoolean(Object value) {
    return (value instanceof Boolean) && (boolean) value;
  }

  /**
   * Decorates the given expression to account for the fact that it is expected to compute a boolean
   * result.
   */
  public static CompiledExpression asBooleanExpression(
      CompiledExpression e, Metadata metadata, long id) throws InterpreterException {
    return e.map(
        (exe, eff) ->
            CompiledExpression.executable(
                stack ->
                    exe.execute(stack)
                        .transformAsync(
                            obj -> immediateValue(expectBoolean(obj, metadata, id)),
                            directExecutor()),
                eff),
        c -> CompiledExpression.constant(expectBoolean(c, metadata, id)),
        t -> CompiledExpression.throwing(t));
  }

  /**
   * Runs the given {@link ExecutableExpression} with an empty stack and a dummy global context,
   * constructing a {@link CompiledExpression} from the resulting value or exception.
   *
   * <p>Note: Using this method requires that the expression did not occur within the scope of any
   * local bindings, and that it does not make use of the global context during execution - either
   * by accessing global variables or by invoking functions that are context-sensitive.
   *
   * <p>Not accessing global variables <i>during execution</i> does not mean that the expression
   * does not <i>mention</i> any global variables, though. In particular, this can happen when parts
   * or all of the expression represent a "suspended" computation. The list of global references
   * must contain <i>all</i> mentioned global variables at their correct positions.
   */
  static CompiledExpression executeStatically(
      ExecutableExpression executable, List<String> globalReferences) {
    return compiledConstantOrThrowing(
        () ->
            Futures.getDone(
                executable.execute(new DynamicEnv(COMPILE_TIME_GLOBAL_CONTEXT, globalReferences))));
  }

  /**
   * Returns a future of the value or exception corresponding to a constant or throwing computation.
   */
  private static FluentFuture<Object> getStaticArgumentFuture(CompiledExpression compiled) {
    if (compiled.isExecutable()) {
      throw new IllegalStateException("non-static argument during constant-folding");
    }
    if (compiled.isConstant()) {
      return immediateValue(compiled.constant());
    }
    return immediateException(compiled.throwing());
  }

  /** Special resolver for global variables during constant folding. */
  private static Supplier<ListenableFuture<Object>> indicateNeededGlobal(String name) {
    return () -> {
      throw new IllegalStateException("access to global variable during constant folding: " + name);
    };
  }

  /** AsyncContext used for compile-time computations. Uses no memoization and a direct executor. */
  private static final AsyncContext COMPILE_TIME_ASYNC_CONTEXT =
      new AsyncContext() {
        @Override
        public <T> ListenableFuture<T> perform(
            Supplier<ListenableFuture<T>> resultFutureSupplier, Object... keys) {
          return resultFutureSupplier.get();
        }

        @Override
        public Executor executor() {
          return directExecutor();
        }

        @Override
        public boolean isRuntime() {
          return false;
        }
      };

  /**
   * {@link GlobalContext} used for compile-time computations. Uses no memoization, a direct
   * executor, and access to global variables results in an {@link IllegalStateException}.
   */
  public static final GlobalContext COMPILE_TIME_GLOBAL_CONTEXT =
      GlobalContext.of(COMPILE_TIME_ASYNC_CONTEXT, EvaluationHelpers::indicateNeededGlobal);

  private EvaluationHelpers() {}
}
