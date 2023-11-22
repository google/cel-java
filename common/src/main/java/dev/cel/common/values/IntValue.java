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

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;

/** IntValue is a simple CelValue wrapper around Java longs. */
@Immutable
public final class IntValue extends CelValue {
  private final long value;

  @Override
  public Long value() {
    return value;
  }

  public long longValue() {
    return value;
  }

  @Override
  public boolean isZeroValue() {
    return value() == 0;
  }

  @Override
  public CelType celType() {
    return SimpleType.INT;
  }

  public static IntValue create(long value) {
    return new IntValue(value);
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= Long.hashCode(value);
    return h;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof IntValue)) {
      return false;
    }

    return ((IntValue) o).value == this.value;
  }

  private IntValue(long value) {
    this.value = value;
  }
}
