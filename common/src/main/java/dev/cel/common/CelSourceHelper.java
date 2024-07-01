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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelCodePointArray;
import java.util.List;
import java.util.Optional;

/**
 * Helper methods for common source handling in CEL.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class CelSourceHelper {

  /** Extract the snippet text that corresponds to {@code line}. */
  public static Optional<String> getSnippet(CelCodePointArray content, int line) {
    checkArgument(line > 0);
    ImmutableList<Integer> lineOffsets = content.lineOffsets();
    int start = findLineOffset(lineOffsets, line);
    if (start == -1) {
      return Optional.empty();
    }
    int end = findLineOffset(lineOffsets, line + 1);
    if (end == -1) {
      end = content.size();
    } else {
      end--;
    }
    return Optional.of(end != start ? content.slice(start, end).toString() : "");
  }

  static int findLineOffset(List<Integer> lineOffsets, int line) {
    if (line == 1) {
      return 0;
    }
    if (line > 1 && line <= lineOffsets.size()) {
      return lineOffsets.get(line - 2);
    }
    return -1;
  }

  private CelSourceHelper() {}
}
