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

import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.annotations.Internal;

/**
 * An object which implements dispatching of function calls.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@ThreadSafe
@Internal
public interface Dispatcher extends FunctionResolver {

  /**
   * Returns an {@link ImmutableCopy} from current instance.
   *
   * @see ImmutableCopy
   */
  ImmutableCopy immutableCopy();

  /**
   * An {@link Immutable} copy of a {@link Dispatcher}. Currently {@link LegacyDispatcher}
   * implementation implements both {@link Dispatcher} and {@link Registrar} and cannot be annotated
   * as {@link Immutable}.
   *
   * <p>Should consider to provide Registrar.dumpAsDispatcher and Registrar.dumpAsAsyncDispatcher
   * instead of letting DefaultDispatcher or AsyncDispatcher to implement both Registrar and
   * Dispatcher. But it requires a global refactoring.
   */
  @Immutable
  interface ImmutableCopy extends Dispatcher {}
}
