package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.context.Context;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Implements an {@link AsyncContext} using memoization. */
public final class DefaultAsyncContext implements AsyncContext {

  private final ConcurrentMap<ImmutableList<Object>, ListenableFuture<?>> memoTable;
  private final Executor executor;
  private final Optional<Context> requestContext;

  public DefaultAsyncContext(Executor executor) {
    // TODO: Tune initial capacity and concurrency level.
    this.memoTable = new ConcurrentHashMap<>();
    this.executor = executor;
    this.requestContext = Optional.empty();
  }

  public DefaultAsyncContext(Executor executor, Context requestContext) {
    // TODO: Tune initial capacity and concurrency level.
    this.memoTable = new ConcurrentHashMap<>();
    this.executor = executor;
    this.requestContext = Optional.of(requestContext);
  }

  @Override
  public Optional<Context> requestContext() {
    return requestContext;
  }

  @Override
  public <T> ListenableFuture<T> perform(
      Supplier<ListenableFuture<T>> futureSupplier, Object... keys) {
    ImmutableList<Object> key = ImmutableList.copyOf(keys);
    // If a new settable future is created by computeIfAbsent, it will be dropped
    // into this reference so that it can subsequently be populated.
    // See the comment below on why this cannot be done within the critical region.
    AtomicReference<SettableFuture<T>> futureReference = new AtomicReference<>();
    ListenableFuture<T> resultFuture =
        typedFuture(
            memoTable.computeIfAbsent(
                key,
                k -> {
                  SettableFuture<T> settableFuture = SettableFuture.create();
                  futureReference.set(settableFuture);
                  return settableFuture;
                }));
    // If the executor is directExecutor(), then pulling on the supplier must be
    // done outside the table's critical region or deadlock can result.
    // (This is mostly important for tests where the executor is in fact
    // directExecutor(), but it is also a good defense for situations where
    // production code accidentally uses a direct executor.)
    @Nullable SettableFuture<T> settableFuture = futureReference.get();
    if (settableFuture != null) {
      // If the memo table already contained a settable future,
      // then this branch will not be taken, thereby avoiding
      // to pull on the future supplier and kicking off redundant
      // work.
      executor.execute(() -> settableFuture.setFuture(obtainSuppliedFuture(futureSupplier)));
    }
    return resultFuture;
  }

  // Obtains the future from its supplier but captures exceptions thrown by the supplier itself,
  // turning them into corresponding failed futures.
  private static <T> ListenableFuture<T> obtainSuppliedFuture(
      Supplier<ListenableFuture<T>> futureSupplier) {
    try {
      return futureSupplier.get();
    } catch (Throwable e) {
      return immediateFailedFuture(e);
    }
  }

  // "Recover" static type information that is lost during the round-trip through the
  // memo table.  Notice that this is only safe as long as there are no key collisions.
  @SuppressWarnings("unchecked")
  private static <T> ListenableFuture<T> typedFuture(ListenableFuture<?> future) {
    return (ListenableFuture) future;
  }

  @Override
  public Executor executor() {
    return executor;
  }
}
