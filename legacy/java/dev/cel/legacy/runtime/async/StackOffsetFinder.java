package dev.cel.legacy.runtime.async;

import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import javax.annotation.Nullable;

/**
 * Interface for finding the stack offset of the named local variable relative to an implicit
 * lexical scoping structure.
 */
@Immutable
@FunctionalInterface
public interface StackOffsetFinder {
  int findStackOffset(@Nullable Metadata metadata, long exprId, String name)
      throws InterpreterException;
}
