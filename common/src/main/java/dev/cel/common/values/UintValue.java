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

package dev.cel.common.values;

import com.google.auto.value.AutoValue;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;

/**
 * UintValue represents CelValue for unsigned longs, leveraging Guava's implementation of {@link
 * UnsignedLong}.
 *
 * <p>TODO: Look into potentially accepting a primitive `long` to avoid boxing/unboxing
 * when the interpreter is augmented to work directly on CelValue.
 */
@AutoValue
@Immutable
public abstract class UintValue extends CelValue {

  @Override
  public abstract UnsignedLong value();

  @Override
  public boolean isZeroValue() {
    return UnsignedLong.ZERO.equals(value());
  }

  @Override
  public CelType celType() {
    return SimpleType.UINT;
  }

  public static UintValue create(UnsignedLong value) {
    return new AutoValue_UintValue(value);
  }
}
