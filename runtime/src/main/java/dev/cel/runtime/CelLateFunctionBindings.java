// Copyright 2024 Google LLC
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Collection of {@link CelFunctionBinding} values which are intended to be created once
 * per-evaluation, rather than once per-program setup.
 */
@Immutable
public final class CelLateFunctionBindings implements CelFunctionResolver {

  private final ImmutableMap<String, CelResolvedOverload> functions;

  private CelLateFunctionBindings(ImmutableMap<String, CelResolvedOverload> functions) {
    this.functions = functions;
  }

  @Override
  public Optional<CelResolvedOverload> findOverloadMatchingArgs(
      String functionName, List<String> overloadIds, Object[] args) throws CelEvaluationException {
    return DefaultDispatcher.findOverloadMatchingArgs(functionName, overloadIds, functions, args);
  }

  public static CelLateFunctionBindings from(CelFunctionBinding... functions) {
    return from(Arrays.asList(functions));
  }

  public static CelLateFunctionBindings from(List<CelFunctionBinding> functions) {
    return new CelLateFunctionBindings(
        functions.stream()
            .collect(
                toImmutableMap(
                    CelFunctionBinding::getOverloadId,
                    CelLateFunctionBindings::createResolvedOverload)));
  }

  private static CelResolvedOverload createResolvedOverload(CelFunctionBinding binding) {
    return CelResolvedOverload.of(
        binding.getOverloadId(),
        (args) -> binding.getDefinition().apply(args),
        binding.isStrict(),
        binding.getArgTypes());
  }
}
