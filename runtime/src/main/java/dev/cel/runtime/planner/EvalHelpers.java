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

import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Interpretable;

final class EvalHelpers {

  static Object evalNonstrictly(Interpretable interpretable, GlobalResolver resolver) {
    try {
      return interpretable.eval(resolver);
    } catch (Exception e) {
      return ErrorValue.create(e);
    }
  }

  private EvalHelpers() {}
}
