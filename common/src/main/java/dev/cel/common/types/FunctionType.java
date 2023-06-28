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

package dev.cel.common.types;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

/** Function type with result and arg types. */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class FunctionType extends CelType {

  @Override
  public abstract CelKind kind();

  @Override
  public String name() {
    return "function";
  }

  public abstract CelType resultType();

  @Override
  public abstract ImmutableList<CelType> parameters();

  public static FunctionType create(CelType resultType, Iterable<CelType> argumentTypes) {
    Preconditions.checkNotNull(resultType);
    Preconditions.checkNotNull(argumentTypes);
    return new AutoValue_FunctionType(
        CelKind.FUNCTION, resultType, ImmutableList.copyOf(argumentTypes));
  }
}
