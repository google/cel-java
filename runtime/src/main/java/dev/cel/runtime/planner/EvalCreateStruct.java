package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueProvider;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Immutable
final class EvalCreateStruct implements CelValueInterpretable {

  private final CelValueProvider valueProvider;
  private final String typeName;


  @SuppressWarnings("Immutable")
  private final String[] keys;

  @SuppressWarnings("Immutable")
  private final CelValueInterpretable[] values;


  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    Map<String, Object> fieldValues = new HashMap<>();
    for (int i = 0; i < keys.length; i++) {
      Object value = values[i].eval(resolver).value();
      fieldValues.put(keys[i], value);
    }

    return valueProvider.newValue(typeName, Collections.unmodifiableMap(fieldValues))
        .orElseThrow(() -> new IllegalArgumentException("Type name not found: " + typeName));
  }

  static EvalCreateStruct create(
      CelValueProvider valueProvider,
      String typeName,
      String[] keys,
      CelValueInterpretable[] values
  ) {
    return new EvalCreateStruct(valueProvider, typeName, keys, values);
  }

  private EvalCreateStruct(
      CelValueProvider valueProvider,
      String typeName,
      String[] keys,
      CelValueInterpretable[] values
  ) {
    this.valueProvider = valueProvider;
    this.typeName = typeName;
    this.keys = keys;
    this.values = values;
  }
}
