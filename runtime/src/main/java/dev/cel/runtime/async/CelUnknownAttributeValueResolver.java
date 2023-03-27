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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.runtime.CelAttribute;

/**
 * Represents a resolver function for an Attribute (determining a value for an unknown attribute).
 *
 * <p>The resolver supports either an async or synchronous style.
 *
 * <p>Error handling: thrown exceptions propagate immediately (as a failed future). To handle an
 * error in the CEL evaluator, return a value that is an instance of an unchecked exception.
 * TODO: checked exceptions get double wrapped but otherwise behave as expected.
 */
@ThreadSafe
public abstract class CelUnknownAttributeValueResolver {

  /**
   * Synchronous style resolver. Implementation should return a value for the given attribute. A
   * thrown exception ends evaluation with a failed future.
   *
   * <p>The lookup may be run in an executor managed by the CEL runtime.
   *
   * <p>The implementation must be effectively immutable: multiple resolve requests for the same
   * attribute must return the same value.
   */
  @FunctionalInterface
  @ThreadSafe
  public interface Resolver {
    Object resolve(CelAttribute attribute) throws Exception;
  }

  /**
   * Async style resolver. Implementation should return a future representing the lookup for the
   * given attribute.
   *
   * <p>A failed future is propagated immediately.
   *
   * <p>The implementation must be effectively immutable: multiple resolve requests for the same
   * attribute must return the same value.
   *
   * <p>Note: the caller is responsible for scheduling the execution of the lookup, and the
   * evaluator will update the async context with the result.
   */
  @FunctionalInterface
  @ThreadSafe
  public interface AsyncResolver {
    ListenableFuture<Object> resolve(CelAttribute attribute);
  }

  /** Package private adapter to a normalized listenable future. */
  abstract ListenableFuture<Object> resolve(
      ListeningExecutorService executorService, CelAttribute attribute);

  private static final class SyncImpl extends CelUnknownAttributeValueResolver {
    private final Resolver resolver;

    public SyncImpl(Resolver resolver) {
      this.resolver = resolver;
    }

    @Override
    ListenableFuture<Object> resolve(
        ListeningExecutorService executorService, CelAttribute attribute) {
      return executorService.submit(() -> resolver.resolve(attribute));
    }
  }

  private static final class AsyncImpl extends CelUnknownAttributeValueResolver {
    private final AsyncResolver resolver;

    public AsyncImpl(AsyncResolver resolver) {
      this.resolver = resolver;
    }

    @Override
    ListenableFuture<Object> resolve(
        ListeningExecutorService executorService, CelAttribute attribute) {
      return resolver.resolve(attribute);
    }
  }

  public static CelUnknownAttributeValueResolver fromResolver(Resolver resolve) {
    return new SyncImpl(resolve);
  }

  public static CelUnknownAttributeValueResolver fromAsyncResolver(AsyncResolver resolve) {
    return new AsyncImpl(resolve);
  }
}
