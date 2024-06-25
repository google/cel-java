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

package dev.cel.common;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.types.CelType;

/** Abstract representation of a CEL variable declaration. */
@AutoValue
public abstract class CelVarDecl {

  /** Fully qualified variable name. */
  public abstract String name();

  /** The type of the variable. */
  public abstract CelType type();

  /** Create a new {@code CelVarDecl} with a given {@code name} and {@code type}. */
  @CheckReturnValue
  public static CelVarDecl newVarDeclaration(String name, CelType type) {
    return new AutoValue_CelVarDecl(name, type);
  }
}
