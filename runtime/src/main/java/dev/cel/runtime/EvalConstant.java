package dev.cel.runtime;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;

@Immutable
final class EvalConstant implements CelValueInterpretable {

  @SuppressWarnings("Immutable")
  private final CelValue constant;


  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    return constant;
  }

  EvalConstant(CelValue constant) {
    this.constant = constant;
  }
}