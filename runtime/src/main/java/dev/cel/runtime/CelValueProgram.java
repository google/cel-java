package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import java.util.Map;

@Immutable
@AutoValue
abstract class CelValueProgram implements CelLiteRuntime.Program {
  private static final CelValueConverter DEFAULT_VALUE_CONVERTER = new CelValueConverter();

  abstract CelValueInterpretable interpretable();

  @Override
  public Object eval() throws CelEvaluationException {
    CelValue evalResult = interpretable().eval(GlobalResolver.EMPTY);
    return DEFAULT_VALUE_CONVERTER.fromCelValueToJavaObject(evalResult);
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    return null;
  }
  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    return null;
  }

  static CelLiteRuntime.Program create(CelValueInterpretable interpretable) {
    return new AutoValue_CelValueProgram(interpretable);
  }
}
