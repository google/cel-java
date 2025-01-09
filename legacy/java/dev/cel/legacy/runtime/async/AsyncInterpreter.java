package dev.cel.legacy.runtime.async;

import dev.cel.expr.CheckedExpr;
import dev.cel.runtime.InterpreterException;
import java.util.List;
import java.util.Map;

/** Interface to an asynchronous (futures-based) CEL interpreter. */
public interface AsyncInterpreter {

  /**
   * Creates an asynchronous interpretable for the given expression.
   *
   * <p>This method may run pre-processing and partial evaluation of the expression it gets passed.
   */
  AsyncInterpretable createInterpretable(CheckedExpr checkedExpr) throws InterpreterException;

  /**
   * Creates an asynchronous interpretable for the given expression (just like {@link
   * #createInterpretable} above). If the compiler discovers that the expression describes a
   * compile-time constant, then that constant's value is returned instead. Interpretable or
   * constant are packaged up in an {@link AsyncInterpretableOrConstant}.
   *
   * <p>When resolving global identifiers, the given mapping from names to compile-time known
   * constants is consulted first. Names not bound in this mapping are resolved at runtime.
   */
  AsyncInterpretableOrConstant createInterpretableOrConstant(
      CheckedExpr checkedExpr,
      Map<String, Object> compileTimeConstants,
      List<String> localVariables)
      throws InterpreterException;
}
