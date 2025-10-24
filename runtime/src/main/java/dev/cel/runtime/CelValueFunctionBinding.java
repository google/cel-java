package dev.cel.runtime;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;

@Immutable
public final class CelValueFunctionBinding {

  private final String overloadId;
  private final ImmutableList<Class<? extends CelValue>> argTypes;
  private final CelValueFunctionOverload definition;

  public String overloadId() {
    return overloadId;
  }

  public ImmutableList<Class<? extends CelValue>> argTypes() {
    return argTypes;
  }

  public CelValueFunctionOverload definition() {
    return definition;
  }

  public static CelValueFunctionBinding from(String overloadId, CelValueFunctionOverload.Nullary impl) {
    return from(overloadId, ImmutableList.of(), (args) -> impl.apply());
  }

  public static <T extends CelValue> CelValueFunctionBinding from(
      String overloadId, Class<T> argType, CelValueFunctionOverload.Unary<T> impl) {
    return from(overloadId, ImmutableList.of(argType), (args) -> impl.apply((T) args[0]));
  }

  public static <T1 extends CelValue, T2 extends CelValue> CelValueFunctionBinding from(String overloadId, Class<T1> argType1, Class<T2> argType2, CelValueFunctionOverload.Binary<T1, T2> impl) {
    return from(overloadId, ImmutableList.of(argType1, argType2), (args) -> impl.apply((T1) args[0], (T2) args[1]));
  }

  public static <T1 extends CelValue, T2 extends CelValue, T3 extends CelValue> CelValueFunctionBinding from(String overloadId, Class<T1> argType1, Class<T2> argType2, Class<T3> argType3, CelValueFunctionOverload.Ternary<T1, T2, T3> impl) {
    return from(overloadId, ImmutableList.of(argType1, argType2, argType3), (args) -> impl.apply((T1) args[0], (T2) args[1], (T3) args[2]));
  }

  public static CelValueFunctionBinding from(String overloadId, ImmutableList<Class<? extends CelValue>> argTypes, CelValueFunctionOverload impl) {
    return new CelValueFunctionBinding(overloadId, argTypes, impl);
  }

  public boolean canHandle(CelValue[] arguments) {
    if (argTypes().size() != arguments.length) {
      return false;
    }

    for (int i = 0; i < argTypes().size(); i++) {
      Class<? extends CelValue> paramType = argTypes().get(i);
      CelValue arg = arguments[i];
      if (!paramType.isInstance(arg)) {
        return false;
      }
    }
    return true;
  }

  private CelValueFunctionBinding(String overloadId, ImmutableList<Class<? extends CelValue>> argTypes, CelValueFunctionOverload definition) {
    this.overloadId = overloadId;
    this.argTypes = argTypes;
    this.definition = definition;
  }
}
