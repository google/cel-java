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
    Object[] argVals = new Object[args.size()];
    for (int i = 0; i < args.size(); i++) {
      CelValueInterpretable arg = args.get(i);
      CelValue resolved = arg.eval(resolver);
      argVals[i] = celValueConverter.fromCelValueToJavaObject(resolved);
    }

    Object result = resolvedOverload.getDefinition().apply(argVals);
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
