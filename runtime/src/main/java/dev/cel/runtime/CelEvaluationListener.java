// Copyright 2023 Google LLC
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

import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.ast.CelExpr;

/**
 * Functional interface for a callback method invoked by the runtime. Implementations must ensure
 * that its instances are unconditionally thread-safe.
 */
@FunctionalInterface
@ThreadSafe
public interface CelEvaluationListener {

  /**
   * Callback method invoked by the CEL runtime as evaluation progresses through the AST.
   *
   * @param expr CelExpr that was evaluated to produce the evaluated result.
   * @param evaluatedResult Evaluated result.
   */
  void callback(CelExpr expr, Object evaluatedResult);

  /** Construct a listener that does nothing. */
  static CelEvaluationListener noOpListener() {
    return (arg1, arg2) -> {};
  }
}
