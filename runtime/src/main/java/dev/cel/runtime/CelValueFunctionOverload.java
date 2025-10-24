package dev.cel.runtime;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;

@Immutable
@FunctionalInterface
public interface CelValueFunctionOverload {

  CelValue apply(CelValue... args) throws CelEvaluationException;

  @Immutable
  interface Nullary {
    CelValue apply() throws CelEvaluationException;
  }

  @Immutable
  interface Unary<T extends CelValue> {
    CelValue apply(T arg) throws CelEvaluationException;
  }

  @Immutable
  interface Binary<T1 extends CelValue, T2 extends CelValue> {
    CelValue apply(T1 arg1, T2 arg2) throws CelEvaluationException;
  }

  @Immutable
  interface Ternary<T1 extends CelValue, T2 extends CelValue, T3 extends CelValue> {
    CelValue apply(T1 arg1, T2 arg2, T3 arg3) throws CelEvaluationException;
  }
}
