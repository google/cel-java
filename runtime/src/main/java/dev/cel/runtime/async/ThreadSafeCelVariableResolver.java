// Copyright 2023 Google LLC
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
import dev.cel.runtime.CelVariableResolver;

/**
 * Trivial subinterface of {@link dev.cel.runtime.CelVariableResovler} that allows for enforcing
 * ThreadSafe errorprone checks.
 *
 * <p>TODO: Check feasibility of applying ThreadSafe annotation directly on
 * CelVariableResolver. Implementations should be effectively immutable for consistent behavior, but
 * in practice the errorprone check may be too restricting for clients. At present, this will break
 * at least a few clients. Alternatively, if this interface adds additional functionality specific
 * to the async runtime, it may make sense to keep.
 */
@ThreadSafe
@FunctionalInterface
public interface ThreadSafeCelVariableResolver extends CelVariableResolver {}
