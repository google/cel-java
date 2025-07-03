package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import java.util.Map;

@Immutable
@AutoValue
abstract class CelValueProgram implements CelLiteRuntime.Program {
  abstract CelValueInterpretable interpretable();

  abstract CelValueConverter celValueConverter();

  @Override
  public Object eval() throws CelEvaluationException {
    CelValue evalResult = interpretable().eval(GlobalResolver.EMPTY);
    return celValueConverter().fromCelValueToJavaObject(evalResult);
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    CelValue evalResult = interpretable().eval(Activation.copyOf(mapValue));
    return celValueConverter().fromCelValueToJavaObject(evalResult);
  }

  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    return null;
  }

  static CelLiteRuntime.Program create(
      CelValueInterpretable interpretable,
      CelValueConverter celValueConverter
  ) {
    return new AutoValue_CelValueProgram(interpretable, celValueConverter);
  }
}
