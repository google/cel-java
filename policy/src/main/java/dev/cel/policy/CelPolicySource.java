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

package dev.cel.policy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.CelSourceHelper;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.Source;
import dev.cel.common.internal.CelCodePointArray;
import java.util.Map;
import java.util.Optional;

/** CelPolicySource represents the source content of a policy and its related metadata. */
@AutoValue
public abstract class CelPolicySource implements Source {

  @Override
  public abstract CelCodePointArray getContent();

  @Override
  public abstract String getDescription();

  @Override
  public abstract ImmutableMap<Long, Integer> getPositionsMap();

  @Override
  public Optional<String> getSnippet(int line) {
    return CelSourceHelper.getSnippet(getContent(), line);
  }

  /**
   * Get the line and column in the source expression text for the given code point {@code offset}.
   */
  public Optional<CelSourceLocation> getOffsetLocation(int offset) {
    return CelSourceHelper.getOffsetLocation(getContent(), offset);
  }

  /** Builder for {@link CelPolicySource}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setContent(CelCodePointArray content);

    public abstract Builder setDescription(String description);

    public abstract Builder setPositionsMap(Map<Long, Integer> value);

    @CheckReturnValue
    public abstract CelPolicySource build();
  }

  public abstract Builder toBuilder();

  public static Builder newBuilder(String content) {
    return newBuilder(CelCodePointArray.fromString(content));
  }

  public static Builder newBuilder(CelCodePointArray celCodePointArray) {
    return new AutoValue_CelPolicySource.Builder()
        .setDescription("")
        .setContent(celCodePointArray)
        .setPositionsMap(ImmutableMap.of());
  }
}
