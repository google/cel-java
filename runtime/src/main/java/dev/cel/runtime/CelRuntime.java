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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import com.google.protobuf.Message;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import java.util.Map;

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
  @Immutable
  interface Program {

    /** Evaluate the expression without any variables. */
    Object eval() throws CelEvaluationException;

    /** Evaluate the expression using a {@code mapValue} as the source of input variables. */
    Object eval(Map<String, ?> mapValue) throws CelEvaluationException;

    /**
     * Evaluate a compiled program with {@code mapValue} and late-bound functions {@code
     * lateBoundFunctionResolver}.
     */
    Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
        throws CelEvaluationException;

    /** Evaluate the expression using {@code message} fields as the source of input variables. */
    Object eval(Message message) throws CelEvaluationException;

    /** Evaluate a compiled program with a custom variable {@code resolver}. */
    Object eval(CelVariableResolver resolver) throws CelEvaluationException;

    /**
     * Evaluate a compiled program with a custom variable {@code resolver} and late-bound functions
     * {@code lateBoundFunctionResolver}.
     */
    Object eval(CelVariableResolver resolver, CelFunctionResolver lateBoundFunctionResolver)
        throws CelEvaluationException;

    /**
     * Trace evaluates a compiled program without any variables and invokes the listener as
     * evaluation progresses through the AST.
     */
    Object trace(CelEvaluationListener listener) throws CelEvaluationException;

    /**
     * Trace evaluates a compiled program using a {@code mapValue} as the source of input variables.
     * The listener is invoked as evaluation progresses through the AST.
     */
    Object trace(Map<String, ?> mapValue, CelEvaluationListener listener)
        throws CelEvaluationException;

    /**
     * Trace evaluates a compiled program using {@code message} fields as the source of input
     * variables. The listener is invoked as evaluation progresses through the AST.
     */
    Object trace(Message message, CelEvaluationListener listener) throws CelEvaluationException;

    /**
     * Trace evaluates a compiled program using a custom variable {@code resolver}. The listener is
     * invoked as evaluation progresses through the AST.
     */
    Object trace(CelVariableResolver resolver, CelEvaluationListener listener)
        throws CelEvaluationException;

    /**
     * Trace evaluates a compiled program using a custom variable {@code resolver} and late-bound
     * functions {@code lateBoundFunctionResolver}. The listener is invoked as evaluation progresses
     * through the AST.
     */
    Object trace(
        CelVariableResolver resolver,
        CelFunctionResolver lateBoundFunctionResolver,
        CelEvaluationListener listener)
        throws CelEvaluationException;

    /**
     * Trace evaluates a compiled program using a {@code mapValue} as the source of input variables
     * and late-bound functions {@code lateBoundFunctionResolver}. The listener is invoked as
     * evaluation progresses through the AST.
     */
    Object trace(
        Map<String, ?> mapValue,
        CelFunctionResolver lateBoundFunctionResolver,
        CelEvaluationListener listener)
        throws CelEvaluationException;

    /**
     * Advance evaluation based on the current unknown context.
     *
     * <p>This represents one round of incremental evaluation and may return a final result or a
     * CelUnknownSet.
     *
     * <p>If no unknowns are declared in the context or {@link CelOptions#enableUnknownTracking()
     * UnknownTracking} is disabled, this is equivalent to eval.
     */
    Object advanceEvaluation(UnknownContext context) throws CelEvaluationException;
  }
}
