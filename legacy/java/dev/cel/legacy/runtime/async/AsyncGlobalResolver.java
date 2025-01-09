package dev.cel.legacy.runtime.async;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * An interface describing an object that can perform a lookup on a given name, returning a future
 * computing the value associated with the so-named global variable.
 */
public interface AsyncGlobalResolver {
  /**
   * Resolves the given name to a future returning its value. The value returned from the future may
   * be null, but the future itself must never be null.
   */
  ListenableFuture<? extends Object> resolve(String name);
}
