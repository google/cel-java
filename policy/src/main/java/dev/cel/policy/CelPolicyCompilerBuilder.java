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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;

/** Interface for building an instance of {@link CelPolicyCompiler} */
public interface CelPolicyCompilerBuilder {

  /** Sets the prefix for the policy variables. Default is `variables.`. */
  @CanIgnoreReturnValue
  CelPolicyCompilerBuilder setVariablesPrefix(String prefix);

  /**
   * Limit the number of iteration while composing rules into a single AST. An exception is thrown
   * if the iteration count exceeds the set value.
   */
  @CanIgnoreReturnValue
  CelPolicyCompilerBuilder setIterationLimit(int iterationLimit);

  /**
   * Enforces the composed AST to stay below the configured depth limit. An exception is thrown if
   * the depth exceeds the configured limit. Setting a negative value disables this check.
   */
  @CanIgnoreReturnValue
  CelPolicyCompilerBuilder setAstDepthLimit(int iterationLimit);

  @CheckReturnValue
  CelPolicyCompiler build();
}
