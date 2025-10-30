// Copyright 2022 Google LLC
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import dev.cel.common.CelErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link Dispatcher}.
 *
 * <p>Should be final, do not mock; mocking {@link Dispatcher} instead.
 */
@ThreadSafe
final class DefaultDispatcher implements Dispatcher {
  public static DefaultDispatcher create() {
    return new DefaultDispatcher();
  }

  @GuardedBy("this")
  private final Map<String, ResolvedOverload> overloads = new HashMap<>();

  public synchronized void add(
      String overloadId, List<Class<?>> argTypes, boolean isStrict, CelFunctionOverload overload) {
    overloads.put(overloadId, CelResolvedOverload.of(overloadId, overload, isStrict, argTypes));
  }

  @Override
  public synchronized Optional<ResolvedOverload> findOverload(
      String functionName, List<String> overloadIds, Object[] args) throws CelEvaluationException {
    return DefaultDispatcher.findOverload(functionName, overloadIds, overloads, args);
  }

  /** Finds the overload that matches the given function name, overload IDs, and arguments. */
  public static Optional<ResolvedOverload> findOverload(
      String functionName,
      List<String> overloadIds,
      Map<String, ? extends ResolvedOverload> overloads,
      Object[] args)
      throws CelEvaluationException {
    int matchingOverloadCount = 0;
    ResolvedOverload match = null;
    List<String> candidates = null;
    for (String overloadId : overloadIds) {
      ResolvedOverload overload = overloads.get(overloadId);
      // If the overload is null, it means that the function was not registered; however, it is
      // possible that the overload refers to a late-bound function.
      if (overload != null && overload.canHandle(args)) {
        if (++matchingOverloadCount > 1) {
          if (candidates == null) {
            candidates = new ArrayList<>();
            candidates.add(match.getOverloadId());
          }
          candidates.add(overloadId);
        }
        match = overload;
      }
    }

    if (matchingOverloadCount > 1) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "Ambiguous overloads for function '%s'. Matching candidates: %s",
              functionName, Joiner.on(", ").join(candidates))
          .setErrorCode(CelErrorCode.AMBIGUOUS_OVERLOAD)
          .build();
    }
    return Optional.ofNullable(match);
  }

  @Override
  public synchronized Dispatcher.ImmutableCopy immutableCopy() {
    return new ImmutableCopy(overloads);
  }

  @Immutable
  private static final class ImmutableCopy implements Dispatcher.ImmutableCopy {
    private final ImmutableMap<String, ResolvedOverload> overloads;

    private ImmutableCopy(Map<String, ResolvedOverload> overloads) {
      this.overloads = ImmutableMap.copyOf(overloads);
    }

    @Override
    public Optional<ResolvedOverload> findOverload(
        String functionName, List<String> overloadIds, Object[] args)
        throws CelEvaluationException {
      return DefaultDispatcher.findOverload(functionName, overloadIds, overloads, args);
    }

    @Override
    public Dispatcher.ImmutableCopy immutableCopy() {
      return this;
    }
  }

  private DefaultDispatcher() {}
}
