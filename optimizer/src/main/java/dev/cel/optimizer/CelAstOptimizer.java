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

package dev.cel.optimizer;

import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.navigation.CelNavigableAst;
import java.util.function.Supplier;

/** Public interface for performing a single, custom optimization on an AST. */
public interface CelAstOptimizer {

  /** Optimizes a single AST. */
  CelAbstractSyntaxTree optimize(CelNavigableAst navigableAst, CelBuilder cel) throws CelOptimizationException;
}
