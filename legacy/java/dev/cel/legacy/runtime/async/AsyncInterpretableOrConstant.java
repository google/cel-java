package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.auto.value.AutoOneOf;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A tagged sum of either an {@link AsyncInterpretable} or a constant.
 *
 * <p>This is returned by the {@link AsyncInterpreter#createInterpretableOrConstant} method.
 */
@AutoOneOf(AsyncInterpretableOrConstant.Kind.class)
public abstract class AsyncInterpretableOrConstant {
  /**
   * Represents the choice between either a dynamic computation on the one hand or a compile-time
   * constant on the other.
   */
  public enum Kind {
    INTERPRETABLE,
    CONSTANT
  }

  public abstract Kind getKind();

  public abstract AsyncInterpretable interpretable();

  static AsyncInterpretableOrConstant interpretable(
      Effect effect, AsyncInterpretable interpretable) {
    return AutoOneOf_AsyncInterpretableOrConstant.interpretable(decorate(effect, interpretable));
  }

  // Constant case uses Optional.empty to describe null.
  public abstract Optional<Object> constant();

  static AsyncInterpretableOrConstant constant(Optional<Object> constant) {
    return AutoOneOf_AsyncInterpretableOrConstant.constant(constant);
  }

  /** Recovers the plain constant (including the possibility of null). */
  @Nullable
  public Object nullableConstant() {
    return constant().orElse(null);
  }

  /**
   * Return an {@link AsyncInterpretable} regardless of kind (converting constants into the
   * corresponding trivial interpretable).
   */
  // This lambda implements @Immutable interface 'AsyncInterpretable', but accesses instance
  // method(s) 'nullableConstant' on 'AsyncInterpretableOrConstant' which is not @Immutable.
  @SuppressWarnings("Immutable")
  public AsyncInterpretable toInterpretable() {
    switch (getKind()) {
      case CONSTANT:
        return decorate(
            Effect.CONTEXT_INDEPENDENT, (gctx, locals) -> immediateFuture(nullableConstant()));
      case INTERPRETABLE:
        return interpretable();
    }
    throw new RuntimeException("[internal] unexpected kind in toInterpretable()");
  }

  private static AsyncInterpretable decorate(Effect newEffect, AsyncInterpretable interpretable) {
    return new AsyncInterpretable() {
      @Override
      public ListenableFuture<Object> evaluate(
          GlobalContext gctx, List<ListenableFuture<Object>> locals) {
        return interpretable.evaluate(gctx, locals);
      }

      @Override
      public Effect effect() {
        return newEffect;
      }
    };
  }
}
