// Copyright 2025 Google LLC
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

package dev.cel.runtime.async;

import com.google.auto.value.AutoValue;
import dev.cel.runtime.CelAttributePattern;

/**
 * CelResolvableAttributePattern wraps {@link CelAttributePattern} to represent a CEL attribute
 * whose value is initially unknown and needs to be resolved. It couples the attribute pattern with
 * a {@link CelUnknownAttributeValueResolver} that can fetch the actual value for the attribute when
 * it becomes available.
 */
@AutoValue
public abstract class CelResolvableAttributePattern {
  public abstract CelAttributePattern attributePattern();

  public abstract CelUnknownAttributeValueResolver resolver();

  public static CelResolvableAttributePattern of(
      CelAttributePattern attribute, CelUnknownAttributeValueResolver resolver) {
    return new AutoValue_CelResolvableAttributePattern(attribute, resolver);
  }
}
