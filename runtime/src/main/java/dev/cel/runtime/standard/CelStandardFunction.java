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

package dev.cel.runtime.standard;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;

/**
 * An abstract class that describes a CEL standard function. An implementation should provide a set
 * of overloads for the standard function
 */
public abstract class CelStandardFunction {
  private final ImmutableSet<CelStandardOverload> overloads;

  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      CelOptions celOptions, RuntimeEquality runtimeEquality) {
    ImmutableSet.Builder<CelFunctionBinding> builder = ImmutableSet.builder();
    for (CelStandardOverload overload : overloads) {
      builder.add(overload.newFunctionBinding(celOptions, runtimeEquality));
    }

    return builder.build();
  }

  CelStandardFunction(ImmutableSet<CelStandardOverload> overloads) {
    checkState(!overloads.isEmpty(), "At least 1 overload must be provided.");
    this.overloads = overloads;
  }
}
