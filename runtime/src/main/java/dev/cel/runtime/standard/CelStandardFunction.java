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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;

/**
 * An abstract class that describes a CEL standard function. An implementation should provide a set
 * of overloads for the standard function
 */
@Immutable
public abstract class CelStandardFunction {
  private final String name;
  private final ImmutableSet<CelStandardOverload> overloads;

  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      CelOptions celOptions, RuntimeEquality runtimeEquality) {
    ImmutableSet<CelFunctionBinding> overloadBindings =
        overloads.stream()
            .map(overload -> overload.newFunctionBinding(celOptions, runtimeEquality))
            .collect(toImmutableSet());

    return CelFunctionBinding.fromOverloads(name, overloadBindings);
  }

  CelStandardFunction(String name, ImmutableSet<CelStandardOverload> overloads) {
    checkArgument(!Strings.isNullOrEmpty(name), "Function name must be provided.");
    checkArgument(!overloads.isEmpty(), "At least 1 overload must be provided.");
    this.overloads = overloads;
    this.name = name;
  }
}
