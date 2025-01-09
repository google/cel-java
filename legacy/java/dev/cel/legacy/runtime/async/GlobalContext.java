package dev.cel.legacy.runtime.async;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.function.Supplier;

/** Represents the global context of the computation: async context and global variable bindings. */
@AutoValue
public abstract class GlobalContext {

  /** Retrieves the {@link AsyncContext}. */
  public abstract AsyncContext context();

  /** Retrieves the {@link AsyncCanonicalResolver}. */
  public abstract AsyncCanonicalResolver resolver();

  /**
   * Creates a new {@link GlobalContext} by pairing an {@link AsyncContext} with the corresponding
   * {@link AsyncCanonicalResolver}.
   */
  public static GlobalContext of(AsyncContext context, AsyncCanonicalResolver resolver) {
    return new AutoValue_GlobalContext(context, resolver);
  }

  /** Resolves a global variable using the {@link AsyncCanonicalResolver}. */
  public Supplier<ListenableFuture<Object>> resolve(String name) {
    return resolver().resolve(name);
  }
}
