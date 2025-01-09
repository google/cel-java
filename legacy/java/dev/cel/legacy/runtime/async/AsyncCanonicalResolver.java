package dev.cel.legacy.runtime.async;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.function.Supplier;

/**
 * An interface describing an object that can perform a lookup on a given name, returning a future
 * computing the value associated with the so-named global variable. The value must be in canonical
 * CEL runtime representation.
 */
public interface AsyncCanonicalResolver {
  /**
   * Resolves the given name to a future returning its value. Neither the returned supplier nor the
   * supplied future can be null, and a value computed by the future must be in canonical CEL
   * runtime representation (which also excludes null). Returns a failed future if the name is not
   * bound or if the value cannot be represented canonically.
   */
  Supplier<ListenableFuture<Object>> resolve(String name);
}
