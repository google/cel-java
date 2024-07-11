// Copyright 2024 Google LLC
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

package dev.cel.policy;

import dev.cel.common.CelAbstractSyntaxTree;

/** Public interface for compiling CEL policies. */
public interface CelPolicyCompiler {

  /**
   * Combines the {@link #compileRule} and {@link #compose} into a single call.
   *
   * <p>This generates a single CEL AST from a collection of policy expressions associated with a CEL
   * environment.
   */
  default CelAbstractSyntaxTree compile(CelPolicy policy) throws CelPolicyValidationException {
   return compose(compileRule(policy));
  }

  /**
   * Produces a {@link CelCompiledRule} from the policy which contains a set of compiled variables and match statements.
   * Compiled rule defines an expression graph, which can be composed into a single expression via {@link #compose} call.
   */
  CelCompiledRule compileRule(CelPolicy policy) throws CelPolicyValidationException;

  /**
   * Composes {@link CelCompiledRule}, representing an expression graph, into a single expression value.
   */
  CelAbstractSyntaxTree compose(CelCompiledRule compiledRule)
      throws CelPolicyValidationException;
}
