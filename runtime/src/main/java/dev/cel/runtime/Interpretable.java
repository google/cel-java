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

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;

/**
 * Represent an expression which can be interpreted repeatedly using a given activation.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public interface Interpretable {

  /** Runs interpretation with the given activation which supplies name/value bindings. */
  Object eval(GlobalResolver resolver) throws InterpreterException;

  /**
   * Runs interpretation with the given activation which supplies name/value bindings.
   *
   * <p>This method allows for evaluation listeners to be provided per-evaluation.
   */
  Object eval(GlobalResolver resolver, CelEvaluationListener listener) throws InterpreterException;

  /**
   * Runs interpretation with the given activation which supplies name/value bindings.
   *
   * <p>This method allows for late-binding functions to be provided per-evaluation, which can be
   * useful for binding functions which might have side-effects that are not observable to CEL
   * directly such as recording telemetry or evaluation state in a more granular fashion than a more
   * general evaluation listener might permit.
   */
  Object eval(
      GlobalResolver resolver,
      FunctionResolver lateBoundFunctionResolver,
      CelEvaluationListener listener)
      throws InterpreterException;
}
