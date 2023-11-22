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

/** DoubleValue is a simple CelValue wrapper around Java doubles. */
@Immutable
public final class DoubleValue extends CelValue {
  private final double value;

  @Override
  public Double value() {
    return value;
  }

  public double doubleValue() {
    return value;
  }

  @Override
  public boolean isZeroValue() {
    return value() == 0;
  }

  @Override
  public CelType celType() {
    return SimpleType.DOUBLE;
  }

  public static DoubleValue create(double value) {
    return new DoubleValue(value);
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= Double.hashCode(value);
    return h;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof DoubleValue)) {
      return false;
    }

    return Double.doubleToLongBits(((DoubleValue) o).doubleValue())
        == Double.doubleToLongBits(this.doubleValue());
  }

  private DoubleValue(double value) {
    this.value = value;
  }
}
