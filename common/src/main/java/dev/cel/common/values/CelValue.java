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
import dev.cel.common.annotations.Internal;

/**
 * A representation of a CEL value for the runtime. Clients should never directly extend from
 * CelValue. Clients may extend some subclasses of CelValue, such as StructValue if it has been
 * explicitly designed and documented for extension.
 */
@Immutable
@Internal
public abstract class CelValue {

  /**
   * The underlying value. This is typically the Java native value or a derived instance of CelValue
   * (ex: an element in lists or key/value pair in maps).
   */
  public abstract Object value();

  /** Returns true if the {@link #value()} is a zero value for its type. */
  public abstract boolean isZeroValue();

  // TOOD(b/309695452): Add CelEquals method
  // TODO: Add a getter for CelType

  public CelValue() {}
}
