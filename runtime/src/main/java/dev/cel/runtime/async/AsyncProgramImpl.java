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

package dev.cel.runtime.async;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transformAsync;
import static com.google.common.util.concurrent.Futures.whenAllSucceed;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.runtime.CelAttribute;
import dev.cel.runtime.CelAttributePattern;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime.Program;
import dev.cel.runtime.CelUnknownSet;
import dev.cel.runtime.UnknownContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Default runtime implementation for {@link CelAsyncRuntime.AsyncProgram}.
 *
 * <p>Control flow is:
 *
 * <ol>
 *   <li>Evaluate the expression (synchronously), identifying unknowns or final result
 *   <li>If unknowns identified, schedule lookups (calls to *Resolver.resolve) on the executor
 *   <li>On any failure, return failed future. CEL errors can be introduced by the return value of
 *       resolver.
 *   <li>On all successful, generate new context for next round of evaluation on the executor
 *   <li>repeat until a final (non-unknown) result, no progress is made, or iteration limit reached.
 * </ol>
 */
@ThreadSafe
final class AsyncProgramImpl implements CelAsyncRuntime.AsyncProgram {
  // Safety limit for resolution rounds.
  private final int maxEvaluateIterations;
  private final Program program;
  private final ListeningExecutorService executor;
  private final ImmutableMap<CelAttributePattern, CelUnknownAttributeValueResolver> resolvers;

  AsyncProgramImpl(
      Program program,
      ListeningExecutorService executor,
      ImmutableMap<CelAttributePattern, CelUnknownAttributeValueResolver> resolvers,
      int maxEvaluateIterations) {
    this.program = program;
    this.executor = executor;
    this.resolvers = resolvers;
    this.maxEvaluateIterations = maxEvaluateIterations;
  }

  private Optional<CelUnknownAttributeValueResolver> lookupResolver(CelAttribute attribute) {
    // TODO: may need to handle multiple resolvers for partial case.
    for (Map.Entry<CelAttributePattern, CelUnknownAttributeValueResolver> entry :
        resolvers.entrySet()) {
      if (entry.getKey().isPartialMatch(attribute)) {
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

  private ListenableFuture<ImmutableMap<CelAttribute, Object>> allAsMapOnSuccess(
      Map<CelAttribute, ListenableFuture<Object>> futureMap) {
    return whenAllSucceed(futureMap.values())
        .call(
            () ->
                futureMap.entrySet().stream()
                    .collect(
                        toImmutableMap(
                            Map.Entry::getKey,
                            entry -> {
                              try {
                                return Futures.getDone(entry.getValue());
                              } catch (ExecutionException e) {
                                throw new AssertionError(
                                    "Futures.whenAllSucceed forwarded failed future", e);
                              }
                            })),
            executor);
  }

  private ListenableFuture<Object> resolveAndReevaluate(
      CelUnknownSet unknowns, UnknownContext ctx, int iteration) {
    Map<CelAttribute, ListenableFuture<Object>> futureMap = new LinkedHashMap<>();
    for (CelAttribute attr : unknowns.attributes()) {
      Optional<CelUnknownAttributeValueResolver> maybeResolver = lookupResolver(attr);

      maybeResolver.ifPresent((resolver) -> futureMap.put(attr, resolver.resolve(executor, attr)));
    }

    if (futureMap.isEmpty()) {
      return immediateFailedFuture(
          new CelEvaluationException(
              String.format("Unknown resolution failed -- no resolvers for: %s", unknowns)));
    }

    // TODO: lookup fails on any failure. Fine for prototyping, but this would likely
    // need to be configurable in the future.
    return transformAsync(
        allAsMapOnSuccess(futureMap),
        (result) -> evalPass(ctx.withResolvedAttributes(result), unknowns, iteration),
        executor);
  }

  private ListenableFuture<Object> evalPass(
      UnknownContext ctx, CelUnknownSet lastSet, int iteration) {
    Object result = null;
    try {
      result = program.advanceEvaluation(ctx);
    } catch (CelEvaluationException e) {
      return immediateFailedFuture(e);
    }
    if (result instanceof CelUnknownSet) {
      if (result.equals(lastSet)) {
        return immediateFailedFuture(
            new CelEvaluationException("No progress in iterative eval. Last result: " + result));
      }
      // Don't handle unknowns if next evaluation would exceed eval limit.
      iteration++;
      if (iteration >= maxEvaluateIterations) {
        return immediateFailedFuture(
            new CelEvaluationException("Max Evaluation iterations exceeded: " + iteration));
      }
      return resolveAndReevaluate((CelUnknownSet) result, ctx, iteration);
    }

    return immediateFuture(result);
  }

  @Override
  public ListenableFuture<Object> evaluateToCompletion(UnknownContext ctx) {
    return evalPass(ctx, CelUnknownSet.create(ImmutableSet.of()), 0);
  }
}
