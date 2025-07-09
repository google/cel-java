package dev.cel.runtime.planner;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.Activation;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationExceptionBuilder;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.CelLiteRuntime.Program;
import dev.cel.runtime.GlobalResolver;
import java.util.Map;

@Immutable
@AutoValue
abstract class CelValueProgram implements Program {
  abstract CelValueInterpretable interpretable();

  abstract CelValueConverter celValueConverter();

  @Override
  public Object eval() throws CelEvaluationException {
    CelValue evalResult = evalOrThrow(interpretable(), GlobalResolver.EMPTY);
    return celValueConverter().fromCelValueToJavaObject(evalResult);
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    CelValue evalResult = evalOrThrow(interpretable(), Activation.copyOf(mapValue));
    return celValueConverter().fromCelValueToJavaObject(evalResult);
  }

  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    throw new UnsupportedOperationException("Late bound functions not supported yet");
  }

  private static CelValue evalOrThrow(CelValueInterpretable interpretable, GlobalResolver resolver) throws CelEvaluationException {
    try {
      CelValue evalResult = interpretable.eval(resolver);
      if (evalResult instanceof ErrorValue) {
        ErrorValue errorValue = (ErrorValue) evalResult;
        Exception e = errorValue.value();
        throw newCelEvaluationException(e);
      }

      return evalResult;
    } catch (RuntimeException e) {
      throw newCelEvaluationException(e);
    }
  }

  private static CelEvaluationException newCelEvaluationException(Exception e) {
    CelEvaluationExceptionBuilder builder;
    if (e instanceof CelRuntimeException) {
      builder = CelEvaluationExceptionBuilder
              .newBuilder((CelRuntimeException) e);
    } else {
      builder = CelEvaluationExceptionBuilder.newBuilder(e.getMessage()).setCause(e);
    }

    return builder.build();
  }

  static Program create(
      CelValueInterpretable interpretable,
      CelValueConverter celValueConverter
  ) {
    return new AutoValue_CelValueProgram(interpretable, celValueConverter);
  }
}
