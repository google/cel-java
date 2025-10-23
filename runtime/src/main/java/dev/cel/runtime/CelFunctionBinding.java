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

/**
 * Binding consisting of an overload id, a Java-native argument signature, and an overload
 * definition.
 *
 * <p>While the CEL function has a human-readable {@code camelCase} name, overload ids should use
 * the following convention where all {@code <type>} names should be ASCII lower-cased. For types
 * prefer the unparameterized simple name of time, or unqualified name of any proto-based type:
 *
 * <ul>
 *   <li>unary member function: <type>_<function>
 *   <li>binary member function: <type>_<function>_<arg_type>
 *   <li>unary global function: <function>_<type>
 *   <li>binary global function: <function>_<arg_type1>_<arg_type2>
 *   <li>global function: <function>_<arg_type1>_<arg_type2>_<arg_typeN>
 * </ul>
 *
 * <p>Examples: string_startsWith_string, mathMax_list, lessThan_money_money
 */
@Immutable
public interface CelFunctionBinding {
  String getOverloadId();

  ImmutableList<Class<?>> getArgTypes();

  CelFunctionOverload getDefinition();

  /** Create a unary function binding from the {@code overloadId}, {@code arg}, and {@code impl}. */
  @SuppressWarnings("unchecked")
  static <T> CelFunctionBinding from(
      String overloadId, Class<T> arg, CelFunctionOverload.Unary<T> impl) {
    return from(overloadId, ImmutableList.of(arg), (args) -> impl.apply((T) args[0]));
  }

  /**
   * Create a binary function binding from the {@code overloadId}, {@code arg1}, {@code arg2}, and
   * {@code impl}.
   */
  @SuppressWarnings("unchecked")
  static <T1, T2> CelFunctionBinding from(
      String overloadId, Class<T1> arg1, Class<T2> arg2, CelFunctionOverload.Binary<T1, T2> impl) {
    return from(
        overloadId, ImmutableList.of(arg1, arg2), (args) -> impl.apply((T1) args[0], (T2) args[1]));
  }

  /** Create a function binding from the {@code overloadId}, {@code argTypes}, and {@code impl}. */
  static CelFunctionBinding from(
      String overloadId, Iterable<Class<?>> argTypes, CelFunctionOverload impl) {
    return new FunctionBindingImpl(overloadId, ImmutableList.copyOf(argTypes), impl);
  }

  default boolean canHandle(Object[] arguments) {
    ImmutableList<Class<?>> parameterTypes = getArgTypes();
    if (parameterTypes.size() != arguments.length) {
      return false;
    }
    for (int i = 0; i < parameterTypes.size(); i++) {
      Class<?> paramType = parameterTypes.get(i);
      Object arg = arguments[i];
      if (arg == null) {
        // Reject nulls. CEL-Java in general is not designed to handle nullability of objects.
        return false;
      }
      if (!paramType.isAssignableFrom(arg.getClass())) {
        return false;
      }
    }
    return true;
  }
}
