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

package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.exceptions.CelAttributeNotFoundException;
import dev.cel.runtime.GlobalResolver;

/** Represents a missing attribute that is surfaced while resolving a struct field or a map key. */
final class MissingAttribute implements Attribute {

  private final ImmutableSet<String> missingAttributes;

  @Override
  public Object resolve(GlobalResolver ctx) {
    throw CelAttributeNotFoundException.forFieldResolution(missingAttributes);
  }

  @Override
  public Attribute addQualifier(Qualifier qualifier) {
    throw new UnsupportedOperationException("Unsupported operation");
  }

  static MissingAttribute newMissingAttribute(String... attributeNames) {
    return newMissingAttribute(ImmutableSet.copyOf(attributeNames));
  }

  static MissingAttribute newMissingAttribute(ImmutableSet<String> attributeNames) {
    return new MissingAttribute(attributeNames);
  }

  private MissingAttribute(ImmutableSet<String> missingAttributes) {
    this.missingAttributes = missingAttributes;
  }
}
