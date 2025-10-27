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

package dev.cel.runtime;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;

/** TODO */
@Immutable
@FunctionalInterface
public interface CelValueFunctionOverload {

  /** TODO */
  CelValue apply(CelValue... args);

  /** TODO */
  @Immutable
  interface Nullary {
    CelValue apply();
  }

  /** TODO */
  @Immutable
  interface Unary<T extends CelValue> {
    CelValue apply(T arg);
  }

  /** TODO */
  @Immutable
  interface Binary<T1 extends CelValue, T2 extends CelValue> {
    CelValue apply(T1 arg1, T2 arg2);
  }

  /** TODO */
  @Immutable
  interface Ternary<T1 extends CelValue, T2 extends CelValue, T3 extends CelValue> {
    CelValue apply(T1 arg1, T2 arg2, T3 arg3);
  }
}
