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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.runtime.CelAttributePattern;
import dev.cel.runtime.CelRuntime;
import java.util.concurrent.ExecutorService;

/** Builder interface for {@link CelAsyncRuntime}. */
public interface CelAsyncRuntimeBuilder {
  public static final int DEFAULT_MAX_EVALUATE_ITERATIONS = 10;

  /** Set the CEL runtime for running incremental evaluation. */
  @CanIgnoreReturnValue
  public CelAsyncRuntimeBuilder setRuntime(CelRuntime runtime);

  /** Add attributes that are declared as Unknown, without any resolver. */
  @CanIgnoreReturnValue
  public CelAsyncRuntimeBuilder addUnknownAttributePatterns(CelAttributePattern... attributes);

  /** Marks an attribute pattern as unknown and associates a resolver with it. */
  @CanIgnoreReturnValue
  public CelAsyncRuntimeBuilder addResolvableAttributePattern(
      CelAttributePattern attribute, CelUnknownAttributeValueResolver resolver);

  /**
   * Set the maximum number of allowed evaluation passes.
   *
   * <p>This is a safety mechanism for expressions that chain dependent unknowns (e.g. via the
   * conditional operator or nested function calls).
   *
   * <p>Implementations should default to {@value DEFAULT_MAX_EVALUATION_ITERATIONS}.
   */
  @CanIgnoreReturnValue
  public CelAsyncRuntimeBuilder setMaxEvaluateIterations(int n);

  /**
   * Sets the variable resolver for simple CelVariable names (e.g. 'x' or 'com.google.x').
   *
   * <p>This is consulted after checking for unknown or resolved attributes. It represents any data
   * about the environment that does not need any special resolution.
   */
  @CanIgnoreReturnValue
  CelAsyncRuntimeBuilder setVariableResolver(ThreadSafeCelVariableResolver variableResolver);

  /**
   * Sets the executorService for generated AsyncPrograms.
   *
   * <p>The executor is used for handling simple transformations of the resolved data and for
   * scheduling subsequent rounds of evaluation. If synchronous style resolvers are provided, they
   * will be run on the same executor.
   *
   * <p>Explicitly setting an executor is mandatory. Pick an appropriate executor for the configured
   * resolvers.
   */
  @CanIgnoreReturnValue
  public CelAsyncRuntimeBuilder setExecutorService(ExecutorService executorService);

  public CelAsyncRuntime build();
}
