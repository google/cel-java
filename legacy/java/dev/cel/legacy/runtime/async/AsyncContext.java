package dev.cel.legacy.runtime.async;

import com.google.common.context.Context;
import com.google.common.context.WithContext;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Represents a snapshot of the "global context" that a context-dependent operations may reference.
 */
public interface AsyncContext {

  /**
   * "Performs" an operation by obtaining the result future from the given supplier. May optimize
   * by, e.g. memoization based on the given keys. (For example, the keys could list the name the
   * overload ID of a strict context-dependent function together with a list of its actual
   * arguments.)
   */
  <T> ListenableFuture<T> perform(
      Supplier<ListenableFuture<T>> resultFutureSupplier, Object... keys);

  /** Retrieves the executor used for the futures-based computation expressed in CEL. */
  Executor executor();

  default Optional<Context> requestContext() {
    return Optional.empty();
  }

  /**
   * Indicates that the evaluation is a runtime evaluation (as opposed to, e.g., constant-folding or
   * other optimizations that occur during compile-time or preprocessing time).
   */
  default boolean isRuntime() {
    return true;
  }

  /**
   * Decouples the given future from whatever executor it is currently using and couples it to this
   * context. Subsequent transformations that specify {@link MoreExecutors#directExecutor} will run
   * on this context's executor.
   */
  default <T> ListenableFuture<T> coupleToExecutor(ListenableFuture<T> f) {
    return FluentFuture.from(f)
        .transform(x -> x, executor())
        .catchingAsync(Throwable.class, Futures::immediateFailedFuture, executor());
  }

  /**
   * Runs the given supplier of a future within the request context, if any, and then couples the
   * result to the executor.
   */
  default <T> ListenableFuture<T> coupleToExecutorInRequestContext(
      Supplier<ListenableFuture<T>> futureSupplier) {
    return coupleToExecutor(
        requestContext()
            .map(
                c -> {
                  try (WithContext wc = WithContext.enter(c)) {
                    return futureSupplier.get();
                  }
                })
            .orElseGet(futureSupplier));
  }
}
