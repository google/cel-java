package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.CelValueFunctionBinding;

final class EvalUnary implements CelValueInterpretable {

  private final CelValueFunctionBinding resolvedOverload;
  private final CelValueInterpretable arg;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    CelValue argVal = arg.eval(resolver);
    return resolvedOverload.definition().apply(argVal);
  }

  static EvalUnary create(CelValueFunctionBinding resolvedOverload, CelValueInterpretable arg) {
    return new EvalUnary(resolvedOverload, arg);
  }

  private EvalUnary(CelValueFunctionBinding resolvedOverload, CelValueInterpretable arg) {
    this.resolvedOverload = resolvedOverload;
    this.arg = arg;
  }
}
