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
import dev.cel.common.annotations.Internal;

/**
 * An interface which provides metadata for syntax nodes.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public interface Metadata {

  /** Returns the location (like a filename) of the interpreted expression. */
  String getLocation();

  /**
   * Returns the character position of the node in the source. This is a 0-based character offset.
   */
  int getPosition(long exprId);

  /** Checks if a source position recorded for the provided expression id. */
  boolean hasPosition(long exprId);
}
