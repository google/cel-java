// Copyright 2025 Google LLC
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
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelRuntime.Program;
import java.util.Map;
import java.util.Optional;

/** Internal implementation of a {@link CelRuntime.Program} */
@AutoValue
@Immutable
abstract class ProgramImpl implements CelRuntime.Program {

  @Override
  public Object eval() throws CelEvaluationException {
    return evalInternal(Activation.EMPTY);
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    return evalInternal(Activation.copyOf(mapValue));
  }

  @Override
  public Object eval(Message message) throws CelEvaluationException {
    return evalInternal(ProtoMessageActivationFactory.fromProto(message, getOptions()));
  }

  @Override
  public Object eval(CelVariableResolver resolver) throws CelEvaluationException {
    return evalInternal((name) -> resolver.find(name).orElse(null));
  }

  @Override
  public Object eval(CelVariableResolver resolver, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    return evalInternal((name) -> resolver.find(name).orElse(null), lateBoundFunctionResolver);
  }

  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    return evalInternal(Activation.copyOf(mapValue), lateBoundFunctionResolver);
  }

  @Override
  public Object trace(CelEvaluationListener listener) throws CelEvaluationException {
    return evalInternal(Activation.EMPTY, listener);
  }

  @Override
  public Object trace(Map<String, ?> mapValue, CelEvaluationListener listener)
      throws CelEvaluationException {
    return evalInternal(Activation.copyOf(mapValue), listener);
  }

  @Override
  public Object trace(Message message, CelEvaluationListener listener)
      throws CelEvaluationException {
    return evalInternal(ProtoMessageActivationFactory.fromProto(message, getOptions()), listener);
  }

  @Override
  public Object trace(CelVariableResolver resolver, CelEvaluationListener listener)
      throws CelEvaluationException {
    return evalInternal((name) -> resolver.find(name).orElse(null), listener);
  }

  @Override
  public Object trace(
      CelVariableResolver resolver,
      CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener)
      throws CelEvaluationException {
    return evalInternal(
        (name) -> resolver.find(name).orElse(null), lateBoundFunctionResolver, listener);
  }

  @Override
  public Object trace(
      Map<String, ?> mapValue,
      CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener)
      throws CelEvaluationException {
    return evalInternal(Activation.copyOf(mapValue), lateBoundFunctionResolver, listener);
  }

  @Override
  public Object advanceEvaluation(UnknownContext context) throws CelEvaluationException {
    return evalInternal(context, Optional.empty(), Optional.empty());
  }

  private Object evalInternal(GlobalResolver resolver) throws CelEvaluationException {
    return evalInternal(UnknownContext.create(resolver), Optional.empty(), Optional.empty());
  }

  private Object evalInternal(GlobalResolver resolver, CelEvaluationListener listener)
      throws CelEvaluationException {
    return evalInternal(UnknownContext.create(resolver), Optional.empty(), Optional.of(listener));
  }

  private Object evalInternal(GlobalResolver resolver, CelFunctionResolver functionResolver)
      throws CelEvaluationException {
    return evalInternal(
        UnknownContext.create(resolver), Optional.of(functionResolver), Optional.empty());
  }

  private Object evalInternal(
      GlobalResolver resolver,
      CelFunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener)
      throws CelEvaluationException {
    return evalInternal(
        UnknownContext.create(resolver),
        Optional.of(lateBoundFunctionResolver),
        Optional.of(listener));
  }

  /**
   * Evaluate an expr node with an UnknownContext (an activation annotated with which attributes are
   * unknown).
   */
  private Object evalInternal(
      UnknownContext context,
      Optional<CelFunctionResolver> lateBoundFunctionResolver,
      Optional<CelEvaluationListener> listener)
      throws CelEvaluationException {
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
      return impl.eval(context.variableResolver());
      // if (lateBoundFunctionResolver.isPresent()) {
      //   return impl.eval(context.variableResolver(), lateBoundFunctionResolver.get(), listener);
      // }
      // return impl.eval(context.variableResolver(), listener);
    } else if (listener.isPresent()) {
      // return impl.eval(context.variableResolver(), listener.get());
    }

    return impl.eval(context.variableResolver());
  }

  /** Get the underlying {@link Interpretable} for the {@code Program}. */
  abstract Interpretable getInterpretable();

  /** Get the {@code CelOptions} configured for this program. */
  abstract CelOptions getOptions();

  /** Instantiate a new {@code Program} from the input {@code interpretable}. */
  static Program from(Interpretable interpretable, CelOptions options) {
    return new AutoValue_ProgramImpl(interpretable, options);
  }
}
