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

package dev.cel.common.internal;

import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndexes;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;

/**
 * An array of code points that contain code points from both the Latin-1 character set and Basic
 * Multilingual Plane.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
@AutoValue
@AutoValue.CopyAnnotations
@SuppressWarnings("Immutable") // char[] is not exposed externally, thus cannot be mutated.
public abstract class BasicCodePointArray extends CelCodePointArray {

  @SuppressWarnings("mutable")
  abstract char[] codePoints();

  abstract int offset();

  static BasicCodePointArray create(
      char[] codePoints, int size, ImmutableList<Integer> lineOffsets) {
    return create(codePoints, 0, lineOffsets, size);
  }

  static BasicCodePointArray create(
      char[] codePoints, int offset, ImmutableList<Integer> lineOffsets, int size) {
    return new AutoValue_BasicCodePointArray(size, checkNotNull(lineOffsets), codePoints, offset);
  }

  @Override
  public BasicCodePointArray slice(int i, int j) {
    checkPositionIndexes(i, j, size());
    return create(codePoints(), offset() + i, lineOffsets(), j - i);
  }

  @Override
  public int get(int index) {
    checkElementIndex(index, size());
    return codePoints()[offset() + index] & 0xffff;
  }

  @Override
  public final String toString() {
    return new String(codePoints(), offset(), size());
  }
}
