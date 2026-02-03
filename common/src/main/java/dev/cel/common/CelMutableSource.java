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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.CelSource.Extension;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExprConverter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the mutable portion of the {@link CelSource}. This is intended for the purposes of
 * augmenting an AST through CEL optimizers.
 */
import com.google.common.collect.ImmutableList;
import dev.cel.common.internal.CelCodePointArray;

// ...

public final class CelMutableSource {

  private String description;
  private final Map<Long, CelMutableExpr> macroCalls;
  private final Set<Extension> extensions;
  private final CelCodePointArray codePoints;
  private final ImmutableList<Integer> lineOffsets;
  private final Map<Long, Integer> positions;

  @CanIgnoreReturnValue
  public CelMutableSource addMacroCalls(long exprId, CelMutableExpr expr) {
    this.macroCalls.put(exprId, checkNotNull(CelMutableExpr.newInstance(expr)));
    return this;
  }

  @CanIgnoreReturnValue
  public CelMutableSource addAllMacroCalls(Map<Long, CelMutableExpr> macroCalls) {
    this.macroCalls.putAll(macroCalls);
    return this;
  }

  @CanIgnoreReturnValue
  public CelMutableSource addAllExtensions(Collection<? extends Extension> extensions) {
    checkNotNull(extensions);
    this.extensions.addAll(extensions);
    return this;
  }

  @CanIgnoreReturnValue
  public CelMutableSource setDescription(String description) {
    this.description = checkNotNull(description);
    return this;
  }

  @CanIgnoreReturnValue
  public CelMutableSource clearMacroCall(long exprId) {
    this.macroCalls.remove(exprId);
    return this;
  }

  @CanIgnoreReturnValue
  public CelMutableSource clearMacroCalls() {
    this.macroCalls.clear();
    return this;
  }

  public String getDescription() {
    return description;
  }

  public Map<Long, CelMutableExpr> getMacroCalls() {
    return macroCalls;
  }

  public Set<Extension> getExtensions() {
    return extensions;
  }

  public CelSource toCelSource() {
    return CelSource.newBuilder(codePoints, lineOffsets)
        .setDescription(description)
        .addAllExtensions(extensions)
        .addPositionsMap(positions)
        .addAllMacroCalls(
            macroCalls.entrySet().stream()
                .collect(
                    toImmutableMap(
                        Entry::getKey, v -> CelMutableExprConverter.fromMutableExpr(v.getValue()))))
        .build();
  }

  public static CelMutableSource newInstance() {
    return new CelMutableSource(
        "",
        new HashMap<>(),
        new HashSet<>(),
        CelCodePointArray.fromString(""),
        ImmutableList.of(),
        new HashMap<>());
  }

  public static CelMutableSource fromCelSource(CelSource source) {
    return new CelMutableSource(
        source.getDescription(),
        source.getMacroCalls().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    v -> CelMutableExprConverter.fromCelExpr(v.getValue()),
                    (prev, next) -> {
                      throw new IllegalStateException(
                          "Unexpected source collision at ID: " + prev.id());
                    },
                    HashMap::new)),
        source.getExtensions(),
        source.getContent(),
        source.getLineOffsets(),
        source.getPositionsMap());
  }

  CelMutableSource(
      String description,
      Map<Long, CelMutableExpr> macroCalls,
      Set<Extension> extensions,
      CelCodePointArray codePoints,
      ImmutableList<Integer> lineOffsets,
      Map<Long, Integer> positions) {
    this.description = checkNotNull(description);
    this.macroCalls = checkNotNull(macroCalls);
    this.extensions = checkNotNull(extensions);
    this.codePoints = checkNotNull(codePoints);
    this.lineOffsets = checkNotNull(lineOffsets);
    this.positions = checkNotNull(positions);
  }
}
