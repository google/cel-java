package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.GlobalResolver;

final class EvalZeroArity implements CelValueInterpretable {

  private final CelFunctionBinding resolvedOverload;
  private final CelValueConverter celValueConverter;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    Object result = resolvedOverload.getDefinition().apply(new Object[0]);
    return celValueConverter.fromJavaObjectToCelValue(result);
  }

  static EvalZeroArity create(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter) {
    return new EvalZeroArity(resolvedOverload, celValueConverter);
  }

  private EvalZeroArity(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter) {
    this.resolvedOverload = resolvedOverload;
    this.celValueConverter = celValueConverter;
  }
}
