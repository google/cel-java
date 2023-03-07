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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;

import dev.cel.common.annotations.Internal;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Implementation of ANTLRv4 CharStream using CelCodePointArray.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class CodePointStream implements CharStream {

  private final String sourceName;
  private final CelCodePointArray codePoints;
  private int position;

  public CodePointStream(String sourceName, CelCodePointArray codePoints) {
    this.sourceName = checkNotNull(sourceName);
    this.codePoints = checkNotNull(codePoints);
    position = 0;
  }

  @Override
  public void consume() {
    if (position >= size()) {
      checkState(LA(1) == IntStream.EOF);
      throw new IllegalStateException("Cannot consume EOF");
    }
    position++;
  }

  @Override
  public int LA(int i) {
    int index;
    switch (Integer.signum(i)) {
      case -1:
        index = position + i;
        if (index < 0) {
          return IntStream.EOF;
        }
        return codePoints.get(index);
      case 0:
        return 0;
      case 1:
        index = position + i - 1;
        if (index >= codePoints.size()) {
          return IntStream.EOF;
        }
        return codePoints.get(index);
      default:
        throw new AssertionError();
    }
  }

  @Override
  public int mark() {
    return -1;
  }

  @Override
  public void release(int marker) {}

  @Override
  public int index() {
    return position;
  }

  @Override
  public void seek(int index) {
    checkArgument(index >= 0 && index <= size());
    position = index;
  }

  @Override
  public int size() {
    return codePoints.size();
  }

  @Override
  public String getSourceName() {
    return sourceName;
  }

  @Override
  public String getText(Interval interval) {
    int index = min(interval.a, size());
    int size = min(interval.b - interval.a + 1, size() - index);
    return codePoints.slice(index, index + size).toString();
  }

  @Override
  public String toString() {
    return codePoints.toString();
  }
}
