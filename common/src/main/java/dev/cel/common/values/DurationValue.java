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
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.time.Duration;

/** DurationValue is a simple CelValue wrapper around {@link java.time.Duration} */
@AutoValue
@Immutable
public abstract class DurationValue extends CelValue {

  @Override
  public abstract Duration value();

  @Override
  public boolean isZeroValue() {
    return value().isZero();
  }

  @Override
  public CelType celType() {
    return SimpleType.DURATION;
  }

  public static DurationValue create(Duration value) {
    return new AutoValue_DurationValue(value);
  }
}
