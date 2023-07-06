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
import dev.cel.common.annotations.Internal;

/** Lists are a parameterized type with the parameter indicating the {@code elemType}. */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class ListType extends CelType {

  @Override
  public abstract CelKind kind();

  @Override
  public abstract String name();

  @Override
  public abstract ImmutableList<CelType> parameters();

  @Override
  public CelType withParameters(ImmutableList<CelType> parameters) {
    Preconditions.checkArgument(parameters.size() == 1);
    return create(parameters.get(0));
  }

  public boolean hasElemType() {
    return !parameters().isEmpty();
  }

  public CelType elemType() {
    return parameters().get(0);
  }

  /**
   * Do not use. This exists for compatibility reason with ListType from checked.proto.
   *
   * <p>Note that a parameterized collection, such as {@code List<Int>} or a {@code Map<Int, Int>},
   * is a type-checker construct. An unparameterized list here is what the runtime looks at after
   * type-erasure, typically for handling dynamic dispatch.
   */
  @Internal
  public static ListType create() {
    return new AutoValue_ListType(CelKind.LIST, "list", ImmutableList.of());
  }

  public static ListType create(CelType elemType) {
    return new AutoValue_ListType(CelKind.LIST, "list", ImmutableList.of(elemType));
  }
}
