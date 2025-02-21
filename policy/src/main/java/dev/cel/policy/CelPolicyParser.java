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

/** CelPolicyParser is the interface for parsing policies into a canonical Policy representation. */
public interface CelPolicyParser {

  /** Parses the input {@code policySource} and returns a {@link CelPolicy}. */
  CelPolicy parse(String policySource) throws CelPolicyValidationException;

  /**
   * Parses the input {@code policySource} and returns a {@link CelPolicy}.
   *
   * <p>The {@code description} may be used to help tailor error messages for the location where the
   * {@code policySource} originates, e.g. a file name or form UI element.
   */
  CelPolicy parse(String policySource, String description) throws CelPolicyValidationException;

  /**
   * TagVisitor declares a set of interfaces for handling custom tags which would otherwise be
   * unsupported within the policy, rule, match, or variable objects.
   *
   * @param <T> Type of the node (ex: YAML).
   */
  interface TagVisitor<T> {

    /**
     * visitPolicyTag accepts a parser context, field id, tag name, yaml node, and parent Policy to
     * allow for continued parsing within a custom tag.
     */
    default void visitPolicyTag(
        PolicyParserContext<T> ctx,
        long id,
        String tagName,
        T node,
        CelPolicy.Builder policyBuilder) {
      ctx.reportError(id, String.format("Unsupported policy tag: %s", tagName));
    }

    /**
     * visitRuleTag accepts a parser context, field id, tag name, yaml node, as well as the parent
     * policy and current rule to allow for continued parsing within custom tags.
     */
    default void visitRuleTag(
        PolicyParserContext<T> ctx,
        long id,
        String tagName,
        T node,
        CelPolicy.Builder policyBuilder,
        CelPolicy.Rule.Builder ruleBuilder) {
      ctx.reportError(id, String.format("Unsupported rule tag: %s", tagName));
    }

    /**
     * visitMatchTag accepts a parser context, field id, tag name, yaml node, as well as the parent
     * policy and current match to allow for continued parsing within custom tags.
     */
    default void visitMatchTag(
        PolicyParserContext<T> ctx,
        long id,
        String tagName,
        T node,
        CelPolicy.Builder policyBuilder,
        CelPolicy.Match.Builder matchBuilder) {
      ctx.reportError(id, String.format("Unsupported match tag: %s", tagName));
    }

    /**
     * visitVariableTag accepts a parser context, field id, tag name, yaml node, as well as the
     * parent policy and current variable to allow for continued parsing within custom tags.
     */
    default void visitVariableTag(
        PolicyParserContext<T> ctx,
        long id,
        String tagName,
        T node,
        CelPolicy.Builder policyBuilder,
        CelPolicy.Variable.Builder variableBuilder) {
      ctx.reportError(id, String.format("Unsupported variable tag: %s", tagName));
    }
  }
}
