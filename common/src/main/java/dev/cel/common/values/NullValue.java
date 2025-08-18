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

/**
 * NullValue represents the value 'null' of 'null_type' according to the CEL specification. One of
 * its primary uses is to represent nulls in JSON data.
 */
@Immutable
public final class NullValue extends CelValue {

  /** Sentinel value for representing NULL. */
  public static final NullValue NULL_VALUE = new NullValue();

  @Override
  public NullValue value() {
    return NULL_VALUE;
  }

  @Override
  public CelType celType() {
    return SimpleType.NULL_TYPE;
  }

  @Override
  public boolean isZeroValue() {
    return true;
  }

  @Override
  public String toString() {
    return "NULL_VALUE";
  }

  private NullValue() {}
}
