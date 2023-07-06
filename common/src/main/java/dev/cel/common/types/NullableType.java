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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.function.Function;

/**
 * Nullable types are a union type which indicate that the value is either the {@code targetType} or
 * {@code SimpleType.NULL_TYPE}
 */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class NullableType extends CelType {

  @Override
  public abstract CelKind kind();

  @Override
  public abstract String name();

  @Override
  public boolean isAssignableFrom(CelType other) {
    return targetType().isAssignableFrom(other) || other.equals(SimpleType.NULL_TYPE);
  }

  public abstract CelType targetType();

  @Override
  public CelType withParameters(ImmutableList<CelType> parameters) {
    return create(targetType().withParameters(parameters));
  }

  @Override
  public CelType withFreshTypeParamVariables(Function<String, String> varNameGenerator) {
    return create(targetType().withFreshTypeParamVariables(varNameGenerator));
  }

  public static NullableType create(CelType targetType) {
    return new AutoValue_NullableType(targetType.kind(), targetType.name(), targetType);
  }
}
