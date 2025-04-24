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
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;

/**
 * Represents an unspecified CEL-Type.
 *
 * <p>This exists purely to maintain compatibility with since the proto-based Type from
 * checked.proto allows instantiation without an underlying kind set. Users should not be using this
 * class directly.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@AutoValue
@CheckReturnValue
@Immutable
@Internal
public abstract class UnspecifiedType extends CelType {

  @Override
  public CelKind kind() {
    return CelKind.UNSPECIFIED;
  }

  @Override
  public String name() {
    return "notset";
  }

  /** Do not use. This exists for compatibility reason with ListType from checked.proto. */
  @Internal
  public static UnspecifiedType create() {
    return new AutoValue_UnspecifiedType();
  }
}
