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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.internal.CelCodePointArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Represents the source content of an expression and related metadata. */
@Immutable
public final class CelSource {

  private static final Splitter LINE_SPLITTER = Splitter.on('\n');

  private final CelCodePointArray codePoints;
  private final String description;
  private final ImmutableList<Integer> lineOffsets;
  private final ImmutableMap<Long, Integer> positions;
  private final ImmutableMap<Long, CelExpr> macroCalls;

  private CelSource(Builder builder) {
    codePoints = checkNotNull(builder.codePoints);
    description = checkNotNull(builder.description);
    positions = checkNotNull(builder.positions.buildOrThrow());
    lineOffsets = checkNotNull(ImmutableList.copyOf(builder.lineOffsets));
    macroCalls = checkNotNull(ImmutableMap.copyOf(builder.macroCalls));
  }

  public CelCodePointArray getContent() {
    return codePoints;
  }

  public String getDescription() {
    return description;
  }

  public ImmutableMap<Long, Integer> getPositionsMap() {
    return positions;
  }

  /**
   * Get the code point offsets (NOT code unit offsets) for new line characters '\n' within the
   * source expression text.
   *
   * <p>NOTE: The indices point to the index just after the '\n' not the index of '\n' itself.
   */
  public ImmutableList<Integer> getLineOffsets() {
    return lineOffsets;
  }

  public ImmutableMap<Long, CelExpr> getMacroCalls() {
    return macroCalls;
  }

  /** See {@link #getLocationOffset(int, int)}. */
  public Optional<Integer> getLocationOffset(CelSourceLocation location) {
    checkNotNull(location);
    return getLocationOffset(location.getLine(), location.getColumn());
  }

  /**
   * Get the code point offset within the source expression text that corresponds with the {@code
   * line} and {@code column}.
   *
   * @param line the line number starting from 1
   * @param column the column number starting from 0
   */
  public Optional<Integer> getLocationOffset(int line, int column) {
    return getLocationOffsetImpl(lineOffsets, line, column);
  }

  /**
   * Get the line and column in the source expression text for the given code point {@code offset}.
   */
  public Optional<CelSourceLocation> getOffsetLocation(int offset) {
    return getOffsetLocationImpl(lineOffsets, offset);
  }

  /**
   * Get the text from the source expression that corresponds to {@code line}.
   *
   * @param line the line number starting from 1.
   */
  public Optional<String> getSnippet(int line) {
    checkArgument(line > 0);
    int start = findLineOffset(lineOffsets, line);
    if (start == -1) {
      return Optional.empty();
    }
    int end = findLineOffset(lineOffsets, line + 1);
    if (end == -1) {
      end = codePoints.size();
    } else {
      end--;
    }
    return Optional.of(end != start ? codePoints.slice(start, end).toString() : "");
  }

  /**
   * Get the code point offset within the source expression text that corresponds with the {@code
   * line} and {@code column}.
   *
   * @param line the line number starting from 1
   * @param column the column number starting from 0
   */
  private static Optional<Integer> getLocationOffsetImpl(
      List<Integer> lineOffsets, int line, int column) {
    checkArgument(line > 0);
    checkArgument(column >= 0);
    int offset = findLineOffset(lineOffsets, line);
    if (offset == -1) {
      return Optional.empty();
    }
    return Optional.of(offset + column);
  }

  /**
   * Get the line and column in the source expression text for the given code point {@code offset}.
   */
  public static Optional<CelSourceLocation> getOffsetLocationImpl(
      List<Integer> lineOffsets, int offset) {
    checkArgument(offset >= 0);
    LineAndOffset lineAndOffset = findLine(lineOffsets, offset);
    return Optional.of(CelSourceLocation.of(lineAndOffset.line, offset - lineAndOffset.offset));
  }

  private static int findLineOffset(List<Integer> lineOffsets, int line) {
    if (line == 1) {
      return 0;
    }
    if (line > 1 && line <= lineOffsets.size()) {
      return lineOffsets.get(line - 2);
    }
    return -1;
  }

  private static LineAndOffset findLine(List<Integer> lineOffsets, int offset) {
    int line = 1;
    for (int index = 0; index < lineOffsets.size(); index++) {
      if (lineOffsets.get(index) > offset) {
        break;
      }
      line++;
    }
    if (line == 1) {
      return new LineAndOffset(line, 0);
    }
    return new LineAndOffset(line, lineOffsets.get(line - 2));
  }

  public Builder toBuilder() {
    return new Builder(codePoints, lineOffsets)
        .setDescription(description)
        .addPositionsMap(positions);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(String text) {
    List<Integer> lineOffsets = new ArrayList<>();
    int lineOffset = 0;
    for (String line : LINE_SPLITTER.split(text)) {
      lineOffset += (int) (line.codePoints().count() + 1);
      lineOffsets.add(lineOffset);
    }
    return new Builder(CelCodePointArray.fromString(text), lineOffsets);
  }

  // TODO: remove this its only for compatibility
  @VisibleForTesting
  static CelSource fromString(String text) {
    return fromString(text, "<input>");
  }

  // TODO: remove this its only for compatibility
  @VisibleForTesting
  static CelSource fromString(String content, String description) {
    return newBuilder(content).setDescription(description).build();
  }

  /** Builder for {@link CelSource}. */
  public static final class Builder {

    private final CelCodePointArray codePoints;
    private final List<Integer> lineOffsets;
    private final ImmutableMap.Builder<Long, Integer> positions;
    private final Map<Long, CelExpr> macroCalls;

    private String description;

    private Builder() {
      this(CelCodePointArray.fromString(""), new ArrayList<>());
    }

    private Builder(CelCodePointArray codePoints, List<Integer> lineOffsets) {
      this.codePoints = checkNotNull(codePoints);
      this.lineOffsets = checkNotNull(lineOffsets);
      this.positions = ImmutableMap.builder();
      this.macroCalls = new HashMap<>();
      description = "";
    }

    @CanIgnoreReturnValue
    public Builder setDescription(String description) {
      this.description = checkNotNull(description);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addLineOffsets(int lineOffset) {
      checkArgument(lineOffset >= 0);
      lineOffsets.add(lineOffset);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addLineOffsets(int... lineOffsets) {
      // Purposefully not using Arrays.asList to avoid int boxing/unboxing.
      for (int index = 0; index != lineOffsets.length; index++) {
        addLineOffsets(lineOffsets[index]);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllLineOffsets(Iterable<Integer> lineOffsets) {
      for (int lineOffset : lineOffsets) {
        addLineOffsets(lineOffset);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addPositionsMap(Map<Long, Integer> positionsMap) {
      checkNotNull(positionsMap);
      this.positions.putAll(positionsMap);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addPositions(long exprId, int position) {
      this.positions.put(exprId, position);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addMacroCalls(long exprId, CelExpr expr) {
      this.macroCalls.put(exprId, expr);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllMacroCalls(Map<Long, CelExpr> macroCalls) {
      this.macroCalls.putAll(macroCalls);
      return this;
    }

    /** See {@link #getLocationOffset(int, int)}. */
    public Optional<Integer> getLocationOffset(CelSourceLocation location) {
      checkNotNull(location);
      return getLocationOffset(location.getLine(), location.getColumn());
    }

    /**
     * Get the code point offset within the source expression text that corresponds with the {@code
     * line} and {@code column}.
     *
     * @param line the line number starting from 1
     * @param column the column number starting from 0
     */
    public Optional<Integer> getLocationOffset(int line, int column) {
      return getLocationOffsetImpl(lineOffsets, line, column);
    }

    /**
     * Get the line and column in the source expression text for the given code point {@code
     * offset}.
     */
    public Optional<CelSourceLocation> getOffsetLocation(int offset) {
      return getOffsetLocationImpl(lineOffsets, offset);
    }

    @CheckReturnValue
    public ImmutableMap<Long, Integer> getPositionsMap() {
      return this.positions.buildOrThrow();
    }

    @CheckReturnValue
    public Map<Long, CelExpr> getMacroCalls() {
      return macroCalls;
    }

    @CheckReturnValue
    public boolean containsMacroCalls(long exprId) {
      return this.macroCalls.containsKey(exprId);
    }

    @CheckReturnValue
    public CelSource build() {
      return new CelSource(this);
    }
  }

  private static final class LineAndOffset {

    private LineAndOffset(int line, int offset) {
      this.line = line;
      this.offset = offset;
    }

    int line;
    int offset;
  }
}
