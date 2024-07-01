// Copyright 2024 Google LLC
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

package dev.cel.common;

import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelCodePointArray;
import java.util.Optional;

/**
 * Common interface definition for source properties.
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should instead use the canonical implementations
 * such as CelSource.
 */
@Internal
public interface Source {

  /** Gets the original textual content of this source, represented in an array of code points. */
  CelCodePointArray getContent();

  /**
   * Gets the description of this source that may optionally be set (example: location of the file
   * containing the source).
   */
  String getDescription();

  /**
   * Get the text from the source text that corresponds to {@code line}. Snippets are split based on
   * the newline ('\n').
   *
   * @param line the line number starting from 1.
   */
  Optional<String> getSnippet(int line);
}
