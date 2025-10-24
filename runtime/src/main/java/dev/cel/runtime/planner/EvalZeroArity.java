package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelValueFunctionBinding;
import dev.cel.runtime.GlobalResolver;

final class EvalZeroArity implements CelValueInterpretable {

  private final CelValueFunctionBinding resolvedOverload;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    return resolvedOverload.definition().apply();
  }

  static EvalZeroArity create(CelValueFunctionBinding resolvedOverload) {
    return new EvalZeroArity(resolvedOverload);
  }

  private EvalZeroArity(CelValueFunctionBinding resolvedOverload) {
    this.resolvedOverload = resolvedOverload;
  }
}
