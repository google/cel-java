package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dev.cel.legacy.runtime.async.Canonicalization.canonicalizeProto;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Message;
import dev.cel.common.CelOptions;
import java.util.function.Supplier;

/** Adapts an {@link AsyncGlobalResolver} to act as an {@link AsyncCanonicalResolver}. */
final class ResolverAdapter implements AsyncCanonicalResolver {

  final AsyncGlobalResolver asyncGlobalResolver;
  private final CelOptions celOptions;

  ResolverAdapter(AsyncGlobalResolver asyncGlobalResolver, CelOptions celOptions) {
    this.asyncGlobalResolver = asyncGlobalResolver;
    this.celOptions = celOptions;
  }

  @Override
  public Supplier<ListenableFuture<Object>> resolve(String name) {
    return () ->
        FluentFuture.from(asyncGlobalResolver.resolve(name))
            .transformAsync(
                value -> {
                  if (value == null) {
                    throw new IllegalArgumentException("name not bound: '" + name + "'");
                  }
                  if (!(value instanceof Message)) {
                    return immediateFuture(value);
                  }
                  return immediateFuture(canonicalizeProto((Message) value, celOptions));
                },
                directExecutor());
  }
}
