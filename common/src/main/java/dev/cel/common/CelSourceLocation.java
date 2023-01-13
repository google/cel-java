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

package dev.cel.common;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

/** Represents a location within {@link CelSource}. */
@AutoValue
@Immutable
public abstract class CelSourceLocation implements Comparable<CelSourceLocation> {

  public static final CelSourceLocation NONE = CelSourceLocation.of(-1, -1);

  // Package-private default constructor to prevent unexpected extensions outside of this codebase.
  CelSourceLocation() {}

  public abstract int getLine();

  public abstract int getColumn();

  @Override
  public final int compareTo(CelSourceLocation other) {
    int otherLine;
    int otherColumn;
    if (other != null) {
      otherLine = positiveOrMax(other.getLine());
      otherColumn = positiveOrMax(other.getColumn());
    } else {
      otherLine = Integer.MAX_VALUE;
      otherColumn = Integer.MAX_VALUE;
    }
    int diff = Integer.compare(positiveOrMax(getLine()), otherLine);
    if (diff != 0) {
      return diff;
    }
    return Integer.compare(positiveOrMax(getColumn()), otherColumn);
  }

  static Builder newBuilder() {
    return new AutoValue_CelSourceLocation.Builder();
  }

  public static CelSourceLocation of(int line, int column) {
    checkArgument(line >= -1 && line < Integer.MAX_VALUE);
    checkArgument(column >= -1 && column < Integer.MAX_VALUE);
    return newBuilder().setLine(line).setColumn(column).build();
  }

  /** Package-private builder for {@link CelSourceLocation}. */
  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setLine(int line);

    abstract Builder setColumn(int column);

    @CheckReturnValue
    abstract CelSourceLocation build();
  }

  private static int positiveOrMax(int value) {
    return value >= 0 ? value : Integer.MAX_VALUE;
  }
}
