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
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

/**
 * A simple type reference to a struct.
 *
 * <p>This can be used if you simply need to refer to a struct (including Proto Messages) in an
 * expression without providing its full definition.
 *
 * <p>If you need the ability to enumerate its fields, use a full-fledged {@link StructType} or
 * {@link ProtoMessageType} instead. An example is to mask fields that should not be exposed by
 * leveraging {@code ProtoTypeMaskTypeProvider}
 */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class StructTypeReference extends CelType {

  @Override
  public abstract CelKind kind();

  @Override
  public abstract String name();

  @Override
  public boolean isAssignableFrom(CelType other) {
    return super.isAssignableFrom(other)
        || ((other instanceof StructType || other instanceof StructTypeReference)
            && other.name().equals(name()));
  }

  public static StructTypeReference create(String name) {
    return new AutoValue_StructTypeReference(CelKind.STRUCT, name);
  }
}
