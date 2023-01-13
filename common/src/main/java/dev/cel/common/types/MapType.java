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

/**
 * Maps are a parameterized type where the two parameters supported indicate the {@code keyType} and
 * {@code valueType} of map entries.
 */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class MapType extends CelType {

  @Override
  public abstract CelKind kind();

  @Override
  public abstract String name();

  @Override
  public abstract ImmutableList<CelType> parameters();

  @Override
  public CelType withParameters(ImmutableList<CelType> parameters) {
    Preconditions.checkArgument(parameters().size() == 2);
    return create(parameters.get(0), parameters.get(1));
  }

  public CelType keyType() {
    return parameters().get(0);
  }

  public CelType valueType() {
    return parameters().get(1);
  }

  public static MapType create(CelType keyType, CelType valueType) {
    // Note, the set of supported key types should be configurable within the type checker
    // as there may be cases where derivations from CEL alter the supported map key set.
    return new AutoValue_MapType(CelKind.MAP, "map", ImmutableList.of(keyType, valueType));
  }
}
