package dev.cel.legacy.runtime.async;

import com.google.common.util.concurrent.FluentFuture;
import com.google.errorprone.annotations.Immutable;

/**
 * An executable expression is the outcome of "compiling" a CEL expression. The compiler effectively
 * implements a form of denotational semantics, and executable expressions are the domain that CEL
 * expressions are mapped to by this semantics.
 *
 * <p>Executable expressions represent the original CEL program, but with most of the interpretative
 * overhead eliminated. In particular, there is no more traversal of abstract syntax during
 * execution, and no dispatch on expression types. Local variables are accessed in constant time by
 * precomputed stack location.
 *
 * <p>Executable expressions are modeled by functions that take the dynamic environment (for
 * resolving free local and global variables) and an executor (for handling asynchronous aspects of
 * the computation) as arguments and return a future of the result.
 *
 * <p>The future is produced without throwing exceptions. Any runtime exceptions are captured by the
 * future itself by letting it fail. Interpretation-related errors are signalled by {@link
 * InterpreterExceptions} that are the cause of the corresponding {@link ExecutionException}.
 */
@Immutable
@FunctionalInterface
public interface ExecutableExpression {
  FluentFuture<Object> execute(DynamicEnv env);
}
