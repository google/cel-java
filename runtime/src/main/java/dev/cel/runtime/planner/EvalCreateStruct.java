package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueProvider;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Immutable
final class EvalCreateStruct implements CelValueInterpretable {

  private final CelValueProvider valueProvider;
  private final String typeName;

  // Regular hashmap used for performance. Planner must not mutate the map post-construction.
  @SuppressWarnings("Immutable")
  private final Map<String, CelValueInterpretable> fields;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    Map<String, Object> fieldValues = new HashMap<>();
    for (Entry<String, CelValueInterpretable> entry : fields.entrySet()) {
      Object value = entry.getValue().eval(resolver).value();
      fieldValues.put(entry.getKey(), value);
    }

    return valueProvider.newValue(typeName, Collections.unmodifiableMap(fieldValues))
        .orElseThrow(() -> new IllegalArgumentException("Type name not found: " + typeName));
  }

  static EvalCreateStruct create(
      CelValueProvider valueProvider,
      String typeName,
      Map<String, CelValueInterpretable> fields
  ) {
    return new EvalCreateStruct(valueProvider, typeName, fields);
  }

  private EvalCreateStruct(
      CelValueProvider valueProvider,
      String typeName,
      Map<String, CelValueInterpretable> fields
  ) {
    this.valueProvider = valueProvider;
    this.typeName = typeName;
    this.fields = fields;
  }
}
