package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.ImmutableListValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalCreateList implements CelValueInterpretable {

  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] values;


  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    ImmutableList.Builder<CelValue> builder = ImmutableList.builder();
    for (int i = 0; i < values.length; i++) {
      builder.add(values[i].eval(resolver));
    }
    return ImmutableListValue.create(builder.build());
  }

  static EvalCreateList create(
      CelValueInterpretable[] values
  ) {
    return new EvalCreateList(values);
  }

  private EvalCreateList(
      CelValueInterpretable[] values
  ) {
    this.values = values;
  }
}