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

import javax.annotation.concurrent.ThreadSafe;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.runtime.CelAttributePattern;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.runtime.CelVariableResolver;
import dev.cel.runtime.UnknownContext;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/** Default {@link CelAsyncRuntime} runtime implementation. See {@link AsyncProgramImpl}. */
@ThreadSafe
final class CelAsyncRuntimeImpl implements CelAsyncRuntime {
  private final ImmutableMap<CelAttributePattern, CelUnknownAttributeValueResolver>
      unknownAttributeResolvers;
  private final ImmutableSet<CelAttributePattern> unknownAttributePatterns;
  private final CelRuntime runtime;
  private final ListeningExecutorService executorService;
  // Client must guarantee that the provided resolver implementation is thread safe.
  private final CelVariableResolver variableResolver;
  private final int maxEvaluateIterations;

  private CelAsyncRuntimeImpl(
      ImmutableMap<CelAttributePattern, CelUnknownAttributeValueResolver> unknownAttributeResolvers,
      ImmutableSet<CelAttributePattern> unknownAttributePatterns,
      CelVariableResolver variableResolver,
      CelRuntime runtime,
      ListeningExecutorService executorService,
      int maxEvaluateIterations) {
    this.unknownAttributeResolvers = unknownAttributeResolvers;
    this.unknownAttributePatterns = unknownAttributePatterns;
    this.variableResolver = variableResolver;
    this.runtime = runtime;
    this.executorService = executorService;
    this.maxEvaluateIterations = maxEvaluateIterations;
  }

  @Override
  public UnknownContext newAsyncContext() {
    return UnknownContext.create(variableResolver, unknownAttributePatterns);
  }

  @Override
  public AsyncProgram createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    return new AsyncProgramImpl(
        runtime.createProgram(ast),
        executorService,
        unknownAttributeResolvers,
        maxEvaluateIterations);
  }

  static Builder newBuilder() {
    return new Builder();
  }

  /** {@link CelAsyncRuntimeBuilder} implementation for {@link CelAsyncRuntimeImpl}. */
  private static final class Builder implements CelAsyncRuntimeBuilder {
    private CelRuntime runtime;
    private final ImmutableSet.Builder<CelAttributePattern> unknownAttributePatterns;
    private final ImmutableMap.Builder<CelAttributePattern, CelUnknownAttributeValueResolver>
        unknownAttributeResolvers;
    private ListeningExecutorService executorService;
    private Optional<CelVariableResolver> variableResolver;
    private int maxEvaluateIterations;

    private Builder() {
      runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
      unknownAttributeResolvers = ImmutableMap.builder();
      unknownAttributePatterns = ImmutableSet.builder();
      variableResolver = Optional.empty();
      maxEvaluateIterations = DEFAULT_MAX_EVALUATE_ITERATIONS;
    }

    @Override
    public Builder setRuntime(CelRuntime runtime) {
      this.runtime = runtime;
      return this;
    }

    @Override
    public Builder addUnknownAttributePatterns(CelAttributePattern... attributes) {
      unknownAttributePatterns.add(attributes);
      return this;
    }

    @Override
    public Builder addResolvableAttributePattern(
        CelAttributePattern attribute, CelUnknownAttributeValueResolver resolver) {
      unknownAttributeResolvers.put(attribute, resolver);
      unknownAttributePatterns.add(attribute);
      return this;
    }

    @Override
    public Builder setMaxEvaluateIterations(int n) {
      Preconditions.checkArgument(n > 0, "maxEvaluateIterations must be positive");
      this.maxEvaluateIterations = n;
      return this;
    }

    @Override
    public Builder setVariableResolver(CelVariableResolver variableResolver) {
      this.variableResolver = Optional.of(variableResolver);
      return this;
    }

    @Override
    public Builder setExecutorService(ExecutorService executorService) {
      this.executorService = MoreExecutors.listeningDecorator(executorService);
      return this;
    }

    @Override
    public CelAsyncRuntime build() {
      Preconditions.checkNotNull(executorService, "executorService must be specified.");
      return new CelAsyncRuntimeImpl(
          unknownAttributeResolvers.buildOrThrow(),
          unknownAttributePatterns.build(),
          variableResolver.orElse((unused) -> Optional.empty()),
          runtime,
          executorService,
          maxEvaluateIterations);
    }
  }
}
