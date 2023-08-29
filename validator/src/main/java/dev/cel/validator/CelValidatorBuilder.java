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

package dev.cel.validator;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;

/** Interface for building an instance of CelValidator. */
public interface CelValidatorBuilder {

  /** Adds one or more validator to perform custom AST validation. */
  @CanIgnoreReturnValue
  CelValidatorBuilder addAstValidators(CelAstValidator... astValidators);

  /** Adds one or more validator to perform custom AST validation. */
  @CanIgnoreReturnValue
  CelValidatorBuilder addAstValidators(Iterable<CelAstValidator> astValidators);

  /** Build a new instance of the {@link CelValidator}. */
  @CheckReturnValue
  CelValidator build();
}
