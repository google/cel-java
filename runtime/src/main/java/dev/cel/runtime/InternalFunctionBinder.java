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
import dev.cel.common.annotations.Internal;

/**
 * A helper to create CelFunctionBinding instances with sensitive controls, such as to toggle the
 * strictness of the function.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class InternalFunctionBinder {

  /**
   * Create a unary function binding from the {@code overloadId}, {@code arg}, {@code impl}, and
   * {@code} isStrict.
   */
  @SuppressWarnings("unchecked")
  public static <T> CelFunctionBinding from(
      String overloadId, Class<T> arg, CelFunctionOverload.Unary<T> impl, boolean isStrict) {
    return from(overloadId, ImmutableList.of(arg), (args) -> impl.apply((T) args[0]), isStrict);
  }

  /**
   * Create a function binding from the {@code overloadId}, {@code argTypes}, {@code impl} and
   * {@code isStrict}.
   */
  public static CelFunctionBinding from(
      String overloadId, Iterable<Class<?>> argTypes, CelFunctionOverload impl, boolean isStrict) {
    return new FunctionBindingImpl(overloadId, ImmutableList.copyOf(argTypes), impl, isStrict);
  }

  private InternalFunctionBinder() {}
}
