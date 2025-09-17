package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.ImmutableMapValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalCreateMap implements CelValueInterpretable {


  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] keys;

  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] values;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    ImmutableMap.Builder<CelValue, CelValue> builder = ImmutableMap.builder();
    for (int i = 0; i < keys.length; i++) {
      builder.put(keys[i].eval(resolver), values[i].eval(resolver));
    }
    return ImmutableMapValue.create(builder.build());
  }

  static EvalCreateMap create(
      CelValueInterpretable[] keys,
      CelValueInterpretable[] values
  ) {
    return new EvalCreateMap(keys, values);
  }

  private EvalCreateMap(
      CelValueInterpretable[] keys,
      CelValueInterpretable[] values
  ) {
    this.keys = keys;
    this.values = values;
  }
}