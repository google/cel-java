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
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.internal.CelCodePointArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Represents the source content of an expression and related metadata. */
@Immutable
@AutoValue
public abstract class CelSource implements Source {

  abstract CelCodePointArray codePoints();

  abstract String description();

  abstract ImmutableList<Integer> lineOffsets();

  abstract ImmutableMap<Long, Integer> positions();

  abstract ImmutableMap<Long, CelExpr> macroCalls();

  abstract ImmutableSet<Extension> extensions();

  @Override
  public CelCodePointArray getContent() {
    return codePoints();
  }

  @Override
  public String getDescription() {
    return description();
  }

  @Override
  public ImmutableMap<Long, Integer> getPositionsMap() {
    return positions();
  }

  /**
   * Get the code point offsets (NOT code unit offsets) for new line characters '\n' within the
   * source expression text.
   *
   * <p>NOTE: The indices point to the index just after the '\n' not the index of '\n' itself.
   */
  public ImmutableList<Integer> getLineOffsets() {
    return lineOffsets();
  }

  public ImmutableMap<Long, CelExpr> getMacroCalls() {
    return macroCalls();
  }

  public ImmutableSet<Extension> getExtensions() {
    return extensions();
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
    return getLocationOffsetImpl(lineOffsets(), line, column);
  }

  /**
   * Get the line and column in the source expression text for the given code point {@code offset}.
   */
  public Optional<CelSourceLocation> getOffsetLocation(int offset) {
    return CelSourceHelper.getOffsetLocation(codePoints(), offset);
  }

  @Override
  public Optional<String> getSnippet(int line) {
    return CelSourceHelper.getSnippet(codePoints(), line);
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
    if (line <= 0 || column < 0) {
      return Optional.empty();
    }
    int offset = CelSourceHelper.findLineOffset(lineOffsets, line);
    if (offset == -1) {
      return Optional.empty();
    }
    return Optional.of(offset + column);
  }

  public Builder toBuilder() {
    return new Builder(codePoints(), lineOffsets())
        .setDescription(description())
        .addPositionsMap(positions())
        .addAllExtensions(extensions())
        .addAllMacroCalls(macroCalls());
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(String text) {
    return newBuilder(CelCodePointArray.fromString(text));
  }

  public static Builder newBuilder(CelCodePointArray codePointArray) {
    return new Builder(codePointArray, codePointArray.lineOffsets());
  }

  /** Builder for {@link CelSource}. */
  public static final class Builder {

    private final CelCodePointArray codePoints;
    private final List<Integer> lineOffsets;
    private final Map<Long, Integer> positions;
    private final Map<Long, CelExpr> macroCalls;
    private final ImmutableSet.Builder<Extension> extensions;

    private final boolean lineOffsetsAlreadyComputed;
    private String description;

    private Builder() {
      this(CelCodePointArray.fromString(""), new ArrayList<>());
    }

    private Builder(CelCodePointArray codePoints, List<Integer> lineOffsets) {
      this.codePoints = checkNotNull(codePoints);
      this.lineOffsets = checkNotNull(lineOffsets);
      this.positions = new HashMap<>();
      this.macroCalls = new HashMap<>();
      this.extensions = ImmutableSet.builder();
      this.description = "";
      this.lineOffsetsAlreadyComputed = !lineOffsets.isEmpty();
    }

    @CanIgnoreReturnValue
    public Builder setDescription(String description) {
      this.description = checkNotNull(description);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addLineOffsets(int lineOffset) {
      checkArgument(lineOffset >= 0);
      checkState(
          !lineOffsetsAlreadyComputed,
          "Line offsets were already been computed through the provided code points.");
      lineOffsets.add(lineOffset);
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
    public Builder removePositions(long exprId) {
      this.positions.remove(exprId);
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

    public ImmutableSet<Extension> getExtensions() {
      return extensions.build();
    }

    /**
     * Adds one or more {@link Extension}s to the source information. Extensions implement set
     * semantics and deduped if same ones are provided.
     */
    @CanIgnoreReturnValue
    public Builder addAllExtensions(Iterable<? extends Extension> extensions) {
      checkNotNull(extensions);
      this.extensions.addAll(extensions);
      return this;
    }

    /**
     * Adds one or more {@link Extension}s to the source information. Extensions implement set
     * semantics and deduped if same ones are provided.
     */
    @CanIgnoreReturnValue
    public Builder addAllExtensions(Extension... extensions) {
      return addAllExtensions(Arrays.asList(extensions));
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
      return CelSourceHelper.getOffsetLocation(codePoints, offset);
    }

    @CheckReturnValue
    public Map<Long, Integer> getPositionsMap() {
      return this.positions;
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
      return new AutoValue_CelSource(
          codePoints,
          description,
          ImmutableList.copyOf(lineOffsets),
          ImmutableMap.copyOf(positions),
          ImmutableMap.copyOf(macroCalls),
          extensions.build());
    }
  }

  /**
   * Tag for an extension that were used while parsing or type checking the source expression. For
   * example, optimizations that require special runtime support may be specified. These are used to
   * check feature support between components in separate implementations. This can be used to
   * either skip redundant work or report an error if the extension is unsupported.
   */
  @AutoValue
  @Immutable
  public abstract static class Extension {

    /** Identifier for the extension. Example: constant_folding */
    abstract String id();

    /**
     * Version info. May be skipped if it isn't meaningful for the extension. (for example
     * constant_folding might always be v0.0).
     */
    abstract Version version();

    /**
     * If set, the listed components must understand the extension for the expression to evaluate
     * correctly.
     */
    abstract ImmutableList<Component> affectedComponents();

    /** Version of the extension */
    @AutoValue
    @Immutable
    public abstract static class Version {

      /**
       * Major version changes indicate different required support level from the required
       * components.
       */
      abstract long major();

      /**
       * Minor version changes must not change the observed behavior from existing implementations,
       * but may be provided informational.
       */
      abstract long minor();

      /** Create a new instance of Version with the provided major and minor values. */
      public static Version of(long major, long minor) {
        return new AutoValue_CelSource_Extension_Version(major, minor);
      }
    }

    /** CEL component specifier. */
    public enum Component {
      /** Unspecified, default. */
      COMPONENT_UNSPECIFIED,
      /** Parser. Converts a CEL string to an AST. */
      COMPONENT_PARSER,
      /** Type checker. Checks that references in an AST are defined and types agree. */
      COMPONENT_TYPE_CHECKER,
      /** Runtime. Evaluates a parsed and optionally checked CEL AST against a context. */
      COMPONENT_RUNTIME
    }

    @CheckReturnValue
    public static Extension create(String id, Version version, Iterable<Component> components) {
      checkNotNull(version);
      checkNotNull(components);
      return new AutoValue_CelSource_Extension(id, version, ImmutableList.copyOf(components));
    }

    @CheckReturnValue
    public static Extension create(String id, Version version, Component... components) {
      return create(id, version, Arrays.asList(components));
    }
  }
}
