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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;

@Immutable
final class FunctionBindingImpl implements CelFunctionBinding {

  private final String overloadId;

  private final ImmutableList<Class<?>> argTypes;

  private final CelFunctionOverload definition;

  private final boolean isStrict;

  @Override
  public String getOverloadId() {
    return overloadId;
  }

  @Override
  public ImmutableList<Class<?>> getArgTypes() {
    return argTypes;
  }

  @Override
  public CelFunctionOverload getDefinition() {
    return definition;
  }

  @Override
  public boolean isStrict() {
    return isStrict;
  }

  FunctionBindingImpl(
      String overloadId,
      ImmutableList<Class<?>> argTypes,
      CelFunctionOverload definition,
      boolean isStrict) {
    this.overloadId = overloadId;
    this.argTypes = argTypes;
    this.definition = definition;
    this.isStrict = isStrict;
  }

  static ImmutableSet<CelFunctionBinding> groupOverloadsToFunction(
      String functionName, ImmutableSet<CelFunctionBinding> overloadBindings) {
    if (overloadBindings.size() == 1) {
      CelFunctionBinding singleBinding = Iterables.getOnlyElement(overloadBindings);
      FunctionBindingImpl functionBindingImpl =
          new FunctionBindingImpl(
              functionName,
              singleBinding.getArgTypes(),
              singleBinding.getDefinition(),
              singleBinding.isStrict());

      return ImmutableSet.of(singleBinding, functionBindingImpl);
    }

    ImmutableSet.Builder<CelFunctionBinding> builder = ImmutableSet.builder();
    for (CelFunctionBinding binding : overloadBindings) {
      // Skip adding overload ids that is exactly the same as the function name
      // example: matches standard function has "matches" and "matches_string" as overloads.
      if (!binding.getOverloadId().equals(functionName)) {
        builder.add(binding);
      }
    }

    // Setup dynamic dispatch
    CelFunctionOverload dynamicDispatchDef =
        args -> {
          for (CelFunctionBinding overload : overloadBindings) {
            // TODO: Avoid checking twice (See CelRuntimeImpl)
            // TODO: Pull canHandle to somewhere else?
            if (CelResolvedOverload.canHandle(args, overload.getArgTypes(), overload.isStrict())) {
              return overload.getDefinition().apply(args);
            }
          }

          throw new IllegalArgumentException("No matching overload for function: " + functionName);
        };

    boolean allOverloadsStrict = overloadBindings.stream().allMatch(CelFunctionBinding::isStrict);
    builder.add(
        new FunctionBindingImpl(
            functionName, ImmutableList.of(), dynamicDispatchDef, allOverloadsStrict));

    return builder.build();
  }
}
