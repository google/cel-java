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

import dev.cel.common.annotations.Internal;
import org.jspecify.annotations.Nullable;

/**
 * An interface describing an object that can perform a lookup on a given name, returning the value
 * associated with the so-named global variable.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@SuppressWarnings("AndroidJdkLibsChecker") // FunctionalInterface added in 24
@FunctionalInterface
@Internal
public interface GlobalResolver {

  /** An empty binder which resolves everything to null. */
  GlobalResolver EMPTY =
      new GlobalResolver() {
        @Override
        public @Nullable Object resolve(String name) {
          return null;
        }

        @Override
        public String toString() {
          return "{}";
        }
      };

  /** Resolves the given name to its value. Returns null if resolution fails. */
  @Nullable Object resolve(String name);
}
