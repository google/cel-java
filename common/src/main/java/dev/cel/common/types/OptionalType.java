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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

/** OptionalType is an opaque type that that represents an optional value. */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class OptionalType extends CelType {

  public static final String NAME = "optional";

  @Override
  public CelKind kind() {
    return CelKind.OPAQUE;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public abstract ImmutableList<CelType> parameters();

  public static OptionalType create(CelType parameter) {
    return new AutoValue_OptionalType(ImmutableList.of(parameter));
  }
}
