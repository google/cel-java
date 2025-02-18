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
import com.google.errorprone.annotations.Immutable;

@Immutable
final class FunctionBindingImpl implements CelFunctionBinding {

  private final String overloadId;

  private final ImmutableList<Class<?>> argTypes;

  private final CelFunctionOverload definition;

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

  FunctionBindingImpl(
      String overloadId, ImmutableList<Class<?>> argTypes, CelFunctionOverload definition) {
    this.overloadId = overloadId;
    this.argTypes = argTypes;
    this.definition = definition;
  }
}
