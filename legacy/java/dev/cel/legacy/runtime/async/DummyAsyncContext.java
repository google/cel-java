package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Implements an {@link AsyncContext} without memoization and using the direct executor. For use in
 * tests only.
 */
public enum DummyAsyncContext implements AsyncContext {
  INSTANCE;

  @Override
  public <T> ListenableFuture<T> perform(
      Supplier<ListenableFuture<T>> futureSupplier, Object... key) {
    return futureSupplier.get();
  }

  @Override
  public Executor executor() {
    return directExecutor();
  }
}
