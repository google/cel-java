package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.CelFunctionBinding;

final class EvalUnary implements CelValueInterpretable {

  private final CelFunctionBinding resolvedOverload;
  private final CelValueConverter celValueConverter;
  private final CelValueInterpretable arg;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    CelValue argVal = arg.eval(resolver);
    Object result = resolvedOverload.getDefinition().apply(new Object[] {argVal.value()});
    return celValueConverter.fromJavaObjectToCelValue(result);
  }

  static EvalUnary create(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter, CelValueInterpretable arg) {
    return new EvalUnary(resolvedOverload, celValueConverter, arg);
  }

  private EvalUnary(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter, CelValueInterpretable arg) {
    this.resolvedOverload = resolvedOverload;
    this.celValueConverter = celValueConverter;
    this.arg = arg;
  }
}
