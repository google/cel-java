package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;

final class EvalCall implements CelValueInterpretable {

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    return null;
  }

  static EvalCall create() {
    return new EvalCall();
  }

  private EvalCall() {
  }
}
