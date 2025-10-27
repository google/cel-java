// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.runtime;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;

/** TODO */
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

  public static CelValueFunctionBinding from(
      String overloadId, CelValueFunctionOverload.Nullary impl) {
    return from(overloadId, ImmutableList.of(), (args) -> impl.apply());
  }

  @SuppressWarnings("unchecked")
  public static <T extends CelValue> CelValueFunctionBinding from(
      String overloadId, Class<T> argType, CelValueFunctionOverload.Unary<T> impl) {
    return from(overloadId, ImmutableList.of(argType), (args) -> impl.apply((T) args[0]));
  }

  @SuppressWarnings("unchecked")
  public static <T1 extends CelValue, T2 extends CelValue> CelValueFunctionBinding from(
      String overloadId,
      Class<T1> argType1,
      Class<T2> argType2,
      CelValueFunctionOverload.Binary<T1, T2> impl) {
    return from(
        overloadId,
        ImmutableList.of(argType1, argType2),
        (args) -> impl.apply((T1) args[0], (T2) args[1]));
  }

  public static <T1 extends CelValue, T2 extends CelValue, T3 extends CelValue>
      CelValueFunctionBinding from(
          String overloadId,
          Class<T1> argType1,
          Class<T2> argType2,
          Class<T3> argType3,
          CelValueFunctionOverload.Ternary<T1, T2, T3> impl) {
    return from(
        overloadId,
        ImmutableList.of(argType1, argType2, argType3),
        (args) -> impl.apply((T1) args[0], (T2) args[1], (T3) args[2]));
  }

  public static CelValueFunctionBinding from(
      String overloadId,
      ImmutableList<Class<? extends CelValue>> argTypes,
      CelValueFunctionOverload impl) {
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

  private CelValueFunctionBinding(
      String overloadId,
      ImmutableList<Class<? extends CelValue>> argTypes,
      CelValueFunctionOverload definition) {
    this.overloadId = overloadId;
    this.argTypes = argTypes;
    this.definition = definition;
  }
}
