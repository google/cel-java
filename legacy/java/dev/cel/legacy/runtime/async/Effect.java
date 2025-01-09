package dev.cel.legacy.runtime.async;

/**
 * Represents the effect that a CEL computation has.
 *
 * <p>Context-independent computations are close approximations of mathematical expressions which
 * denote one particular value. Context-independent functions yield equal results when applied to
 * equal argumnents. These computations are marked as {@link Effect#CONTEXT_INDEPENDENT}.
 *
 * <p>Context-dependent computations implicitly reference the "current state of the world". They are
 * not permitted to change the state of the world, but their value may depend on it. An optimizer
 * may drop unneeded context-dependent sub-expressions It is even permissible to reorder calls and
 * to perform common subexpression elimination involving context-dependent computations (under the
 * assumption that the relevant parts of the state of the world do not change during an invocation
 * of {@link AsyncInterpretable#eval}). However, evaluation must not be moved from runtime to
 * compile time. These computations are marked as {@link Effect#CONTEXT_DEPENDENT}.
 */
public enum Effect {
  // No effect, independent of context.  May be compile-time evaluated.
  CONTEXT_INDEPENDENT {
    @Override
    public Effect meet(Effect other) {
      return other;
    }
  },
  // Has read effects on the context but must otherwise be pure. Must not be compile-time
  // evaluated, but can be subject to reordering, CSE, elimination of unused computations,
  // memoization (within one activation context), etc.
  CONTEXT_DEPENDENT {
    @Override
    public Effect meet(Effect other) {
      return this;
    }
  };

  /**
   * Combines effects, taking the greatest lower bound. This is based on a view where
   * DEFERRING_CONTEXT_INDEPENDENT is lower than CONTEXT_INDEPENDENT and CONTEXT_DEPENDENT is lower
   * than both.
   */
  public abstract Effect meet(Effect other);
}
