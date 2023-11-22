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
import dev.cel.common.types.OpaqueType;

/** OpaqueValue is the value representation of OpaqueType. */
@AutoValue
@AutoValue.CopyAnnotations
@SuppressWarnings("Immutable") // Java Object is mutable.
public abstract class OpaqueValue extends CelValue {

  @Override
  public boolean isZeroValue() {
    return false;
  }

  @Override
  public abstract OpaqueType celType();

  public static OpaqueValue create(String name, Object value) {
    return new AutoValue_OpaqueValue(value, OpaqueType.create(name));
  }
}
