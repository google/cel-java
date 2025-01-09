package dev.cel.legacy.runtime.async;

import dev.cel.expr.Type;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.Types;
import dev.cel.runtime.InterpreterException;
import java.util.Map;

/**
 * Represents a {@link CompiledExpression}, possibly further scoped, together with its node ID in
 * the abstract syntax and its CEL type.
 */
@AutoValue
public abstract class IdentifiedCompiledExpression {

  /**
   * Represents a compiled expression when placed within the scope of some additional stack slots
   * (aka local variables).
   */
  @Immutable
  @FunctionalInterface
  public interface ScopedExpression {
    CompiledExpression inScopeOf(String... slots) throws InterpreterException;
  }

  /** The scoped expression in question. */
  public abstract ScopedExpression scopedExpression();

  /** The expression with no further scoping. */
  public CompiledExpression expression() throws InterpreterException {
    return scopedExpression().inScopeOf();
  }

  /** The node ID of the expression in the original checked expression. */
  public abstract long id();

  /** The CEL type of the expression. */
  public abstract Type type();

  /** Constructs an {@link IdentifiedCompiledExpression}. */
  public static IdentifiedCompiledExpression of(
      ScopedExpression scopedExpression, long id, Map<Long, Type> typeMap) {
    return new AutoValue_IdentifiedCompiledExpression(
        scopedExpression, id, typeMap.getOrDefault(id, Types.DYN));
  }
}
