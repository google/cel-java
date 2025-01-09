package dev.cel.legacy.runtime.async;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import java.util.List;

/** Represent an expression which can be interpreted repeatedly with varying global variables. */
@Immutable
@FunctionalInterface
public interface AsyncInterpretable {

  /**
   * Runs interpretation with the given list of local inputs using the given {@link GlobalContext}.
   * The resolver within that context is a canonical resolver, so it supplies global name/value
   * bindings in canonical CEL runtime representation. It is a precondition failure for the number
   * of locals to not match the number of locals that were specified when the {@link
   * AsyncInterpretable} was created. The executor given by the context is used to run any
   * asynchronous aspects of the interpretation. Errors encountered during interpretation result in
   * a failed future that throws an ExecutionException whose cause is an {@link
   * InterpreterException} or a {@link RuntimeException} for things like division by zero etc.
   */
  ListenableFuture<Object> evaluate(
      GlobalContext globalContext, List<ListenableFuture<Object>> locals);

  /**
   * Runs interpretation with the given list of local inputs as well as a resolver that supplies
   * global name/value bindings. Globally resolved values do not have to be in canonical CEL runtime
   * representation, but it must be possible to "adapt" them (i.e., coerce them into that
   * representation).
   *
   * <p>It is a precondition failure for the number of locals to not match the number of locals that
   * were specified when the {@link AsyncInterpretable} was created. The executor given by the
   * context is used to run any asynchronous aspects of the interpretation. Errors encountered
   * during interpretation result in a failed future that throws an ExecutionException whose cause
   * is an {@link InterpreterException} or a {@link RuntimeException} for things like division by
   * zero etc.
   */
  default ListenableFuture<Object> eval(
      AsyncContext context, AsyncGlobalResolver global, List<ListenableFuture<Object>> locals) {
    // Use of the legacy features with ResolverAdapter is generally unsafe. In all known cases this
    // method will be overridden such that the correct behavior occurs; however, this method should
    // be treated with caution.
    return evaluate(
        GlobalContext.of(context, new ResolverAdapter(global, CelOptions.LEGACY)), locals);
  }

  /** Backward-compatible convenience wrapper without locals. */
  default ListenableFuture<Object> eval(AsyncContext context, AsyncGlobalResolver global) {
    return eval(context, global, ImmutableList.of());
  }

  /**
   * Indicates whether or not this interpretable depends on context (reads global variables or calls
   * other context-dependent functions).
   *
   * <p>Assumed to be context-dependent unless explicitly overridden.
   */
  default Effect effect() {
    return Effect.CONTEXT_DEPENDENT;
  }
}
