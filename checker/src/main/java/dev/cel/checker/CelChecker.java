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

package dev.cel.checker;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationResult;

/** Public interface for type-checking parsed CEL expressions. */
@Immutable
public interface CelChecker {

  /**
   * Check the input {@code ast} for type-agreement and return a {@code CelValidationResult}.
   *
   * <p>Check validates the type-agreement of the parsed {@code CelAbstractSyntaxTree}.
   */
  CelValidationResult check(CelAbstractSyntaxTree ast);
}
