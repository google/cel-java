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
import com.google.errorprone.annotations.Immutable;

/**
 * EnumValue is a simple CelValue wrapper around Java enums.
 *
 * <p>Note: CEL-Java currently does not support strongly typed enum. This value class will not be
 * used until the said support is added in.
 */
@AutoValue
@Immutable(containerOf = "E")
public abstract class EnumValue<E extends Enum<E>> extends CelValue {

  @Override
  public abstract Enum<E> value();

  @Override
  public boolean isZeroValue() {
    return false;
  }

  public static <E extends Enum<E>> EnumValue<E> create(Enum<E> value) {
    return new AutoValue_EnumValue<>(value);
  }
}
