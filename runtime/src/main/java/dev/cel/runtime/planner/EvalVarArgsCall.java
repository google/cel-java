package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelValueFunctionBinding;
import dev.cel.runtime.GlobalResolver;

@SuppressWarnings("Immutable")
final class EvalVarArgsCall implements CelValueInterpretable {

  private final CelValueFunctionBinding resolvedOverload;
  private final CelValueInterpretable[] args;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    CelValue[] argVals = new CelValue[args.length];
    for (int i = 0; i < args.length; i++) {
      CelValueInterpretable arg = args[i];
      argVals[i] = arg.eval(resolver);
    }

    return resolvedOverload.definition().apply(argVals);
  }

  static EvalVarArgsCall create(CelValueFunctionBinding resolvedOverload, CelValueInterpretable[] args) {
    return new EvalVarArgsCall(resolvedOverload, args);
  }

  private EvalVarArgsCall(CelValueFunctionBinding resolvedOverload, CelValueInterpretable[] args) {
    this.resolvedOverload = resolvedOverload;
    this.args = args;
  }
}
