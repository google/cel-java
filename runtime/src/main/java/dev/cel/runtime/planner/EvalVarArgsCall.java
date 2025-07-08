package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.GlobalResolver;
final class EvalVarArgsCall implements CelValueInterpretable{

  private final CelFunctionBinding resolvedOverload;
  private final CelValueConverter celValueConverter;
  private final ImmutableList<CelValueInterpretable> args;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    ImmutableList.Builder<Object> argValBuilder = ImmutableList.builder();
    for (CelValueInterpretable arg : args) {
      argValBuilder.add(arg.eval(resolver).value());
    }
    ImmutableList<Object> argVals = argValBuilder.build();

    Object result = resolvedOverload.getDefinition().apply(argVals.toArray());
    return celValueConverter.fromJavaObjectToCelValue(result);
  }

  static EvalVarArgsCall create(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter, ImmutableList<CelValueInterpretable> s) {
    return new EvalVarArgsCall(resolvedOverload, celValueConverter, s);
  }

  private EvalVarArgsCall(CelFunctionBinding resolvedOverload, CelValueConverter celValueConverter, ImmutableList<CelValueInterpretable> args) {
    this.resolvedOverload = resolvedOverload;
    this.celValueConverter = celValueConverter;
    this.args = args;
  }
}
