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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.runtime.UnknownContext;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/** Default {@link CelAsyncRuntime} runtime implementation. See {@link AsyncProgramImpl}. */
@ThreadSafe
final class CelAsyncRuntimeImpl implements CelAsyncRuntime {
  private final CelRuntime runtime;
  private final ListeningExecutorService executorService;
  private final ThreadSafeCelVariableResolver variableResolver;
  private final int maxEvaluateIterations;

  private CelAsyncRuntimeImpl(
      ThreadSafeCelVariableResolver variableResolver,
      CelRuntime runtime,
      ListeningExecutorService executorService,
      int maxEvaluateIterations) {
    this.variableResolver = variableResolver;
    this.runtime = runtime;
    this.executorService = executorService;
    this.maxEvaluateIterations = maxEvaluateIterations;
  }

  private UnknownContext newAsyncContext() {
    return UnknownContext.create(variableResolver, ImmutableList.of());
  }

  @Override
  public AsyncProgram createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    return new AsyncProgramImpl(
        runtime.createProgram(ast),
        executorService,
        maxEvaluateIterations,
        newAsyncContext());
  }

  static Builder newBuilder() {
    return new Builder();
  }

  /** {@link CelAsyncRuntimeBuilder} implementation for {@link CelAsyncRuntimeImpl}. */
  private static final class Builder implements CelAsyncRuntimeBuilder {
    private CelRuntime runtime;
    private ListeningExecutorService executorService;
    private Optional<ThreadSafeCelVariableResolver> variableResolver;
    private int maxEvaluateIterations;

    private Builder() {
      runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
      variableResolver = Optional.empty();
      maxEvaluateIterations = DEFAULT_MAX_EVALUATE_ITERATIONS;
    }

    @Override
    public Builder setRuntime(CelRuntime runtime) {
      this.runtime = runtime;
      return this;
    }

    @Override
    public Builder setMaxEvaluateIterations(int n) {
      Preconditions.checkArgument(n > 0, "maxEvaluateIterations must be positive");
      this.maxEvaluateIterations = n;
      return this;
    }

    @Override
    public Builder setVariableResolver(ThreadSafeCelVariableResolver variableResolver) {
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
          variableResolver.orElse((unused) -> Optional.empty()),
          runtime,
          executorService,
          maxEvaluateIterations);
    }
  }
}
