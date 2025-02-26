// Copyright 2025 Google LLC
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

import dev.cel.common.formats.ParserContext;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Rule;
import dev.cel.policy.CelPolicy.Variable;

/**
 * PolicyParserContext declares a set of interfaces for creating and managing metadata specifically
 * for {@link CelPolicy}.
 */
public interface PolicyParserContext<T> extends ParserContext<T> {
  CelPolicy parsePolicy(PolicyParserContext<T> ctx, T node);

  Rule parseRule(PolicyParserContext<T> ctx, CelPolicy.Builder policyBuilder, T node);

  Match parseMatch(PolicyParserContext<T> ctx, CelPolicy.Builder policyBuilder, T node);

  Variable parseVariable(PolicyParserContext<T> ctx, CelPolicy.Builder policyBuilder, T node);
}
