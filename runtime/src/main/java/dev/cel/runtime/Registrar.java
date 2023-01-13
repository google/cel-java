// Copyright 2022 Google LLC
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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import java.util.List;

/**
 * An object which registers the functions that a {@link Dispatcher} calls.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public interface Registrar {

  /** Interface to a general function. */
  @Immutable
  @FunctionalInterface
  interface Function {
    @CanIgnoreReturnValue
    Object apply(Object[] args) throws InterpreterException;
  }

  /**
   * Interface to a typed unary function without activation argument. Convenience for the {@code
   * add} methods.
   */
  @Immutable
  @FunctionalInterface
  interface UnaryFunction<T> {
    Object apply(T arg) throws InterpreterException;
  }

  /**
   * Interface to a typed binary function without activation argument. Convenience for the {@code
   * add} methods.
   */
  @Immutable
  @FunctionalInterface
  interface BinaryFunction<T1, T2> {
    Object apply(T1 arg1, T2 arg2) throws InterpreterException;
  }

  /** Adds a unary function to the dispatcher. */
  <T> void add(String overloadId, Class<T> argType, UnaryFunction<T> function);

  /** Adds a binary function to the dispatcher. */
  <T1, T2> void add(
      String overloadId, Class<T1> argType1, Class<T2> argType2, BinaryFunction<T1, T2> function);

  /** Adds a general function to the dispatcher. */
  void add(String overloadId, List<Class<?>> argTypes, Function function);
}
