package dev.cel.legacy.runtime.async;

import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateException;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateValue;

import com.google.auto.value.AutoOneOf;
import com.google.common.base.Pair;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A compiled expression is either an {@link ExecutableExpression} (representing a residual
 * computation that must be performed at runtime), or a compile-time constant, or an exception that
 * is known to be thrown. The compile time constant is used during constant-folding (aka
 * compile-time evaluation).
 */
@AutoOneOf(CompiledExpression.Kind.class)
public abstract class CompiledExpression {
  /** Type discriminator for CompiledExpression. */
  public enum Kind {
    EXECUTABLE_WITH_EFFECT, // Run-time residual computation.
    COMPILED_CONSTANT, // Compile-time known value, using Optional to avoid null.
    THROWING // Statically known to throw when executed.
  }

  abstract Kind getKind();

  public abstract Pair<ExecutableExpression, Effect> executableWithEffect();

  public static CompiledExpression executable(ExecutableExpression expression, Effect effect) {
    return AutoOneOf_CompiledExpression.executableWithEffect(Pair.of(expression, effect));
  }

  abstract Optional<Object> compiledConstant();

  static CompiledExpression compiledConstant(Optional<Object> value) {
    return AutoOneOf_CompiledExpression.compiledConstant(value);
  }

  public abstract Throwable throwing();

  public static CompiledExpression throwing(Throwable t) {
    return AutoOneOf_CompiledExpression.throwing(t);
  }

  /** Returns effect information. */
  public Effect effect() {
    switch (getKind()) {
      case EXECUTABLE_WITH_EFFECT:
        return executableWithEffect().getSecond();
      default:
        return Effect.CONTEXT_INDEPENDENT;
    }
  }

  /** Creates a constant expression directly from the nullable constant value. */
  public static CompiledExpression constant(@Nullable Object value) {
    return compiledConstant(Optional.ofNullable(value));
  }

  /** Returns the actual constant value (which may be null). */
  @Nullable
  public Object constant() {
    return compiledConstant().orElse(null);
  }

  /** Determise whether or not the expression represents a residual computation. */
  public boolean isExecutable() {
    return getKind().equals(Kind.EXECUTABLE_WITH_EFFECT);
  }

  /** Determines whether or not the expression represents a constant. */
  public boolean isConstant() {
    return getKind().equals(Kind.COMPILED_CONSTANT);
  }

  /** Determines whether or not the expression throws an exception. */
  public boolean isThrowing() {
    return getKind().equals(Kind.THROWING);
  }

  /**
   * Maps the current expression to some result given three mapping functions, one for each case.
   */
  public <R, E extends Throwable> R map(
      EffectMapping<ExecutableExpression, ? extends R, ? extends E> mappingForExecutable,
      Mapping<Object, ? extends R, ? extends E> mappingForConstant,
      Mapping<Throwable, ? extends R, ? extends E> mappingForThrowing)
      throws E {
    switch (getKind()) {
      case EXECUTABLE_WITH_EFFECT:
        return mappingForExecutable.map(
            executableWithEffect().getFirst(), executableWithEffect().getSecond());
      case COMPILED_CONSTANT:
        return mappingForConstant.map(constant());
      case THROWING:
        return mappingForThrowing.map(throwing());
    }
    throw unexpected("CompiledExpression#map");
  }

  /** Coerces the expression to an executable expression, preserving behavior. */
  // This lambda implements @Immutable interface 'ExecutableExpression', but 'Object' is mutable
  @SuppressWarnings("Immutable")
  public ExecutableExpression toExecutable() {
    return map(
        (exe, eff) -> exe, v -> stack -> immediateValue(v), t -> stack -> immediateException(t));
  }

  /**
   * Maps the current expression to another CompiledExpressions given mappings for executables and
   * constants. The mapping for throwing is the identity.
   *
   * <p>It must be the case that whenever the current expression throws an exception, the
   * constructed expression will throw the same exception.
   */
  public <E extends Throwable> CompiledExpression mapNonThrowing(
      Mapping<ExecutableExpression, ExecutableExpression, E> mappingForExecutable,
      Mapping<Object, CompiledExpression, E> mappingForConstant)
      throws E {
    return map(
        (e, effect) -> executable(mappingForExecutable.map(e), effect),
        mappingForConstant,
        CompiledExpression::throwing);
  }

  /** Represents a generic mapping from A to B, possibly throwing an E. */
  public interface Mapping<A, B, E extends Throwable> {
    B map(A argument) throws E;
  }

  /** Like {@code Mapping<A, B, E>} but carries an extra argument to convey effect information. */
  public interface EffectMapping<A, B, E extends Throwable> {
    B map(A argument, Effect effect) throws E;
  }

  /**
   * Creates a dummy exception to be thrown in places where we don't expect control to reach (but
   * the Java compiler is not smart enough to know that).
   */
  private static RuntimeException unexpected(String where) {
    return new RuntimeException("[internal] reached unexpected program point: " + where);
  }
}
