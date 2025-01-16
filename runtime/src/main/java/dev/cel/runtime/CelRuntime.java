// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import com.google.protobuf.Message;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import java.util.Map;
import java.util.Optional;

/**
 * The CelRuntime creates executable {@code Program} instances from {@code CelAbstractSyntaxTree}
 * values.
 */
@ThreadSafe
public interface CelRuntime {

  /** Creates a {@code Program} from the input {@code ast}. */
  @CanIgnoreReturnValue
  Program createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException;

  CelRuntimeBuilder toRuntimeBuilder();

  /** Creates an evaluable {@code Program} instance which is thread-safe and immutable. */
  @AutoValue
  @Immutable
  abstract class Program {

    /** Evaluate the expression without any variables. */
    public Object eval() throws CelEvaluationException {
      return evalInternal(Activation.EMPTY);
    }

    /** Evaluate the expression using a {@code mapValue} as the source of input variables. */
    public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
      return evalInternal(Activation.copyOf(mapValue));
    }

    /** Evaluate the expression using {@code message} fields as the source of input variables. */
    public Object eval(Message message) throws CelEvaluationException {
      return evalInternal(Activation.fromProto(message, getOptions()));
    }

    /** Evaluate a compiled program with a custom variable {@code resolver}. */
    public Object eval(CelVariableResolver resolver) throws CelEvaluationException {
      return evalInternal((name) -> resolver.find(name).orElse(null));
    }

    /**
     * Evaluate a compiled program with a custom variable {@code resolver} and late-bound functions
     * {@code lateBoundFunctionResolver}.
     */
    public Object eval(CelVariableResolver resolver, CelFunctionResolver lateBoundFunctionResolver)
        throws CelEvaluationException {
      return evalInternal(
          (name) -> resolver.find(name).orElse(null),
          lateBoundFunctionResolver,
          CelEvaluationListener.noOpListener());
    }

    /**
     * Evaluate a compiled program with {@code mapValue} and late-bound functions {@code
     * lateBoundFunctionResolver}.
     */
    public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
        throws CelEvaluationException {
      return evalInternal(
          Activation.copyOf(mapValue),
          lateBoundFunctionResolver,
          CelEvaluationListener.noOpListener());
    }

    /**
     * Trace evaluates a compiled program without any variables and invokes the listener as
     * evaluation progresses through the AST.
     */
    public Object trace(CelEvaluationListener listener) throws CelEvaluationException {
      return evalInternal(Activation.EMPTY, listener);
    }

    /**
     * Trace evaluates a compiled program using a {@code mapValue} as the source of input variables.
     * The listener is invoked as evaluation progresses through the AST.
     */
    public Object trace(Map<String, ?> mapValue, CelEvaluationListener listener)
        throws CelEvaluationException {
      return evalInternal(Activation.copyOf(mapValue), listener);
    }

    /**
     * Trace evaluates a compiled program using {@code message} fields as the source of input
     * variables. The listener is invoked as evaluation progresses through the AST.
     */
    public Object trace(Message message, CelEvaluationListener listener)
        throws CelEvaluationException {
      return evalInternal(Activation.fromProto(message, getOptions()), listener);
    }

    /**
     * Trace evaluates a compiled program using a custom variable {@code resolver}. The listener is
     * invoked as evaluation progresses through the AST.
     */
    public Object trace(CelVariableResolver resolver, CelEvaluationListener listener)
        throws CelEvaluationException {
      return evalInternal((name) -> resolver.find(name).orElse(null), listener);
    }

    /**
     * Trace evaluates a compiled program using a custom variable {@code resolver} and late-bound
     * functions {@code lateBoundFunctionResolver}. The listener is invoked as evaluation progresses
     * through the AST.
     */
    public Object trace(
        CelVariableResolver resolver,
        CelFunctionResolver lateBoundFunctionResolver,
        CelEvaluationListener listener)
        throws CelEvaluationException {
      return evalInternal(
          (name) -> resolver.find(name).orElse(null), lateBoundFunctionResolver, listener);
    }

    /**
     * Trace evaluates a compiled program using a {@code mapValue} as the source of input variables
     * and late-bound functions {@code lateBoundFunctionResolver}. The listener is invoked as
     * evaluation progresses through the AST.
     */
    public Object trace(
        Map<String, ?> mapValue,
        CelFunctionResolver lateBoundFunctionResolver,
        CelEvaluationListener listener)
        throws CelEvaluationException {
      return evalInternal(Activation.copyOf(mapValue), lateBoundFunctionResolver, listener);
    }

    /**
     * Advance evaluation based on the current unknown context.
     *
     * <p>This represents one round of incremental evaluation and may return a final result or a
     * CelUnknownSet.
     *
     * <p>If no unknowns are declared in the context or {@link CelOptions#enableUnknownTracking()
     * UnknownTracking} is disabled, this is equivalent to eval.
     */
    public Object advanceEvaluation(UnknownContext context) throws CelEvaluationException {
      return evalInternal(context, Optional.empty(), CelEvaluationListener.noOpListener());
    }

    private Object evalInternal(GlobalResolver resolver) throws CelEvaluationException {
      return evalInternal(
          UnknownContext.create(resolver), Optional.empty(), CelEvaluationListener.noOpListener());
    }

    private Object evalInternal(GlobalResolver resolver, CelEvaluationListener listener)
        throws CelEvaluationException {
      return evalInternal(UnknownContext.create(resolver), Optional.empty(), listener);
    }

    private Object evalInternal(
        GlobalResolver resolver,
        CelFunctionResolver lateBoundFunctionResolver,
        CelEvaluationListener listener)
        throws CelEvaluationException {
      return evalInternal(
          UnknownContext.create(resolver), Optional.of(lateBoundFunctionResolver), listener);
    }

    /**
     * Evaluate an expr node with an UnknownContext (an activation annotated with which attributes
     * are unknown).
     */
    private Object evalInternal(
        UnknownContext context,
        Optional<CelFunctionResolver> lateBoundFunctionResolver,
        CelEvaluationListener listener)
        throws CelEvaluationException {
      try {
        Interpretable impl = getInterpretable();
        if (getOptions().enableUnknownTracking()) {
          Preconditions.checkState(
              impl instanceof UnknownTrackingInterpretable,
              "Environment misconfigured. Requested unknown tracking without a compatible"
                  + " implementation.");

          UnknownTrackingInterpretable interpreter = (UnknownTrackingInterpretable) impl;
          return interpreter.evalTrackingUnknowns(
              RuntimeUnknownResolver.builder()
                  .setResolver(context.variableResolver())
                  .setAttributeResolver(context.createAttributeResolver())
                  .build(),
              lateBoundFunctionResolver,
              listener);
        } else {
          if (lateBoundFunctionResolver.isPresent()) {
            return impl.eval(context.variableResolver(), lateBoundFunctionResolver.get(), listener);
          }
          return impl.eval(context.variableResolver(), listener);
        }
      } catch (InterpreterException e) {
        throw unwrapOrCreateEvaluationException(e);
      }
    }

    /** Get the underlying {@link Interpretable} for the {@code Program}. */
    abstract Interpretable getInterpretable();

    /** Get the {@code CelOptions} configured for this program. */
    abstract CelOptions getOptions();

    /** Instantiate a new {@code Program} from the input {@code interpretable}. */
    static Program from(Interpretable interpretable, CelOptions options) {
      return new AutoValue_CelRuntime_Program(interpretable, options);
    }

    @CheckReturnValue
    private static CelEvaluationException unwrapOrCreateEvaluationException(
        InterpreterException e) {
      if (e.getCause() instanceof CelEvaluationException) {
        return (CelEvaluationException) e.getCause();
      }

      return new CelEvaluationException(e.getMessage(), e.getCause(), e.getErrorCode(), false);
    }
  }

  /**
   * Binding consisting of an overload id, a Java-native argument signature, and an overload
   * definition.
   *
   * <p>While the CEL function has a human-readable {@code camelCase} name, overload ids should use
   * the following convention where all {@code <type>} names should be ASCII lower-cased. For types
   * prefer the unparameterized simple name of time, or unqualified name of any proto-based type:
   *
   * <ul>
   *   <li>unary member function: <type>_<function>
   *   <li>binary member function: <type>_<function>_<arg_type>
   *   <li>unary global function: <function>_<type>
   *   <li>binary global function: <function>_<arg_type1>_<arg_type2>
   *   <li>global function: <function>_<arg_type1>_<arg_type2>_<arg_typeN>
   * </ul>
   *
   * <p>Examples: string_startsWith_string, mathMax_list, lessThan_money_money
   */
  @AutoValue
  @Immutable
  abstract class CelFunctionBinding {

    public abstract String getOverloadId();

    abstract ImmutableList<Class<?>> getArgTypes();

    abstract CelFunctionOverload getDefinition();

    /**
     * Create a unary function binding from the {@code overloadId}, {@code arg}, and {@code impl}.
     */
    @SuppressWarnings("unchecked")
    public static <T> CelFunctionBinding from(
        String overloadId, Class<T> arg, CelFunctionOverload.Unary<T> impl) {
      return new AutoValue_CelRuntime_CelFunctionBinding(
          overloadId, ImmutableList.of(arg), (args) -> impl.apply((T) args[0]));
    }

    /**
     * Create a binary function binding from the {@code overloadId}, {@code arg1}, {@code arg2}, and
     * {@code impl}.
     */
    @SuppressWarnings("unchecked")
    public static <T1, T2> CelFunctionBinding from(
        String overloadId,
        Class<T1> arg1,
        Class<T2> arg2,
        CelFunctionOverload.Binary<T1, T2> impl) {
      return new AutoValue_CelRuntime_CelFunctionBinding(
          overloadId,
          ImmutableList.of(arg1, arg2),
          (args) -> impl.apply((T1) args[0], (T2) args[1]));
    }

    /**
     * Create a function binding from the {@code overloadId}, {@code argTypes}, and {@code impl}.
     */
    public static CelFunctionBinding from(
        String overloadId, Iterable<Class<?>> argTypes, CelFunctionOverload impl) {
      return new AutoValue_CelRuntime_CelFunctionBinding(
          overloadId, ImmutableList.copyOf(argTypes), impl);
    }
  }
}
