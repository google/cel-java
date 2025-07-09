package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.GlobalResolver;

final class EvalVarArgsCall implements CelValueInterpretable {

  private final CelFunctionBinding resolvedOverload;
  private final CelValueConverter celValueConverter;
  private final ImmutableList<CelValueInterpretable> args;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    ImmutableList.Builder<Object> argValBuilder = ImmutableList.builder();
    for (CelValueInterpretable arg : args) {
      Object evalValue = celValueConverter.fromCelValueToJavaObject(arg.eval(resolver));
      argValBuilder.add(evalValue);
    }
    ImmutableList<Object> argVals = argValBuilder.build();

    Object result = resolvedOverload.getDefinition().apply(argVals.toArray());
    return celValueConverter.fromJavaObjectToCelValue(result);
  }

  static EvalVarArgsCall create(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter, ImmutableList<CelValueInterpretable> args) {
    return new EvalVarArgsCall(resolvedOverload, celValueConverter, args);
  }

  private EvalVarArgsCall(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter, ImmutableList<CelValueInterpretable> args) {
    this.resolvedOverload = resolvedOverload;
    this.celValueConverter = celValueConverter;
    this.args = args;
  }
}
