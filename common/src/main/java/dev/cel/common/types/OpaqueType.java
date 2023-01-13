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
import java.util.Arrays;

/**
 * An opaque type's properties may only be accessed via function calls associated with the type.
 *
 * <p>Apart from protobuf messages, opaque types are the preferred extension mechanism for
 * introducing first-order types into CEL.
 */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class OpaqueType extends CelType {

  @Override
  public abstract CelKind kind();

  @Override
  public abstract String name();

  @Override
  public abstract ImmutableList<CelType> parameters();

  @Override
  public CelType withParameters(ImmutableList<CelType> parameters) {
    return create(name(), parameters);
  }

  public static OpaqueType create(String typeName, CelType... parameters) {
    return create(typeName, ImmutableList.copyOf(Arrays.asList(parameters)));
  }

  public static OpaqueType create(String typeName, ImmutableList<CelType> parameters) {
    return new AutoValue_OpaqueType(CelKind.OPAQUE, typeName, parameters);
  }
}
