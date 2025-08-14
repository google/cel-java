package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueProvider;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import java.util.HashMap;

@Immutable
final class EvalCreateObject implements CelValueInterpretable {

  private final CelValueProvider valueProvider;
  private final String typeName;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    return valueProvider.newValue(typeName, new HashMap<>()).orElseThrow(() -> new IllegalArgumentException("Type name not found: " + typeName));
  }

  static EvalCreateObject create(
      CelValueProvider valueProvider,
      String typeName
  ) {
    return new EvalCreateObject(valueProvider, typeName);
  }

  private EvalCreateObject(
      CelValueProvider valueProvider,
      String typeName
  ) {
    this.valueProvider = valueProvider;
    this.typeName = typeName;
  }
}
