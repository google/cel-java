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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.policy.CelPolicyParser.TagVisitor;

/**
 * Interface for building an instance of {@link CelPolicyParser}.
 *
 * @param <T> Type of the node (Ex: YAML).
 */
public interface CelPolicyParserBuilder<T> {

  /** Adds a custom tag visitor to allow for handling of custom tags. */
  @CanIgnoreReturnValue
  CelPolicyParserBuilder<T> addTagVisitor(TagVisitor<T> tagVisitor);

  /** Builds a new instance of {@link CelPolicyParser}. */
  @CheckReturnValue
  CelPolicyParser build();
}
