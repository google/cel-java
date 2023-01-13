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

package dev.cel.common.types;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

/** The {@code TypeType} is a type which holds a reference to a type-kind. */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class TypeType extends CelType {

  static final TypeType TYPE = create(SimpleType.DYN);

  @Override
  public CelKind kind() {
    return CelKind.TYPE;
  }

  @Override
  public String name() {
    return "type";
  }

  @Override
  public abstract ImmutableList<CelType> parameters();

  public CelType type() {
    return parameters().get(0);
  }

  @Override
  public CelType withParameters(ImmutableList<CelType> parameters) {
    Preconditions.checkArgument(parameters.size() == 1);
    return create(parameters.get(0));
  }

  public static TypeType create(CelType type) {
    return new AutoValue_TypeType(ImmutableList.of(type));
  }
}
