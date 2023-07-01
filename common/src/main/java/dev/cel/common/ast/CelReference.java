// Copyright 2023 Google LLC
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

package dev.cel.common.ast;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Describes a resolved reference to a declaration.
 *
 * <p>This is the CEL-Java native type equivalent of Reference message type from checked.proto.
 */
@AutoValue
@Internal
@Immutable
public abstract class CelReference {
  public abstract String name();

  public abstract ImmutableList<String> overloadIds();

  public abstract Optional<CelConstant> value();

  /** Builder for configuring the {@link CelReference} */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setValue(CelConstant value);

    abstract ImmutableList<String> overloadIds();

    abstract Optional<CelConstant> value();

    abstract ImmutableList.Builder<String> overloadIdsBuilder();

    @CanIgnoreReturnValue
    public Builder addOverloadIds(String... overloadIds) {
      checkNotNull(overloadIds);
      return addOverloadIds(Arrays.asList(overloadIds));
    }

    @CanIgnoreReturnValue
    public Builder addOverloadIds(Iterable<String> overloadIds) {
      checkNotNull(overloadIds);
      this.overloadIdsBuilder().addAll(overloadIds);
      return this;
    }

    @CheckReturnValue
    abstract CelReference autoBuild();

    public final CelReference build() {
      CelReference celReference = autoBuild();
      Preconditions.checkArgument(
          !value().isPresent() || overloadIds().isEmpty(),
          "Value and overloadIds cannot be set at the same time.");
      return celReference;
    }
  }

  public static CelReference.Builder newBuilder() {
    return new AutoValue_CelReference.Builder().setName("");
  }
}
