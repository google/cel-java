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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import java.util.Optional;

/** TODO */
@Internal
@AutoValue
@Immutable
public abstract class CelValueDispatcher {

  abstract ImmutableMap<String, CelValueFunctionBinding> overloads();

  public Optional<CelValueFunctionBinding> findOverload(String overloadId) {
    return Optional.ofNullable(overloads().get(overloadId));
  }

  public static Builder newBuilder() {
    return new AutoValue_CelValueDispatcher.Builder();
  }

  /** TODO */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract ImmutableMap.Builder<String, CelValueFunctionBinding> overloadsBuilder();

    @CanIgnoreReturnValue
    public Builder addOverload(CelValueFunctionBinding functionBinding) {
      overloadsBuilder().put(functionBinding.overloadId(), functionBinding);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addDynamicDispatchOverload(
        String functionName, CelValueFunctionOverload definition) {
      overloadsBuilder()
          .put(
              functionName,
              CelValueFunctionBinding.from(functionName, ImmutableList.of(), definition));

      return this;
    }

    @CheckReturnValue
    public abstract CelValueDispatcher build();
  }
}
