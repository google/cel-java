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

package dev.cel.runtime;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * AsyncContext represents the state required for an iterative evaluation session in the CEL
 * evaluator.
 *
 * <p>It manages the effective state for an evaluation pass in {@link
 * CelRuntime.Program#advanceEvaluation(UnknownContext)}.
 *
 * <p>This class is conditionally thread-safe.
 *
 * <ul>
 *   <li>Resolved values are type-erased, but must be immutable for this class to remain thread
 *       safe.
 *   <li>The class implementing the variable resolver interface must be thread-safe.
 * </ul>
 */
@Immutable
public class UnknownContext {
  private final ImmutableList<CelAttributePattern> unresolvedAttributes;

  // GlobalResolver implementation must be Immutable if used in this class.
  @SuppressWarnings("Immutable")
  @ThreadSafe.Suppress
  private final GlobalResolver variableResolver;

  // Clients must resolve attributes to Immutable types.
  @SuppressWarnings("Immutable")
  @ThreadSafe.Suppress
  private final ImmutableMap<CelAttribute, Object> resolvedAttributes;

  private UnknownContext(
      GlobalResolver resolver,
      ImmutableList<CelAttributePattern> unresolvedAttributes,
      ImmutableMap<CelAttribute, Object> resolvedAttributes) {
    this.unresolvedAttributes = unresolvedAttributes;
    variableResolver = resolver;
    this.resolvedAttributes = resolvedAttributes;
  }

  /**
   * Creates a trivial unknown context from a GlobalResolver.
   *
   * <p>This is used for compatibility with {@link CelRuntime.Program} overloads that don't specify
   * any attributes.
   */
  public static UnknownContext create(GlobalResolver resolver) {
    return new UnknownContext(resolver, ImmutableList.of(), ImmutableMap.of());
  }

  /** Creates an unknown context from a list */
  public static UnknownContext create(
      CelVariableResolver resolver, Collection<CelAttributePattern> attributes) {
    return new UnknownContext(
        createExprVariableResolver(resolver), ImmutableList.copyOf(attributes), ImmutableMap.of());
  }

  /** Adapts a CelVariableResolver to the legacy impl equivalent GlobalResolver. */
  private static GlobalResolver createExprVariableResolver(CelVariableResolver resolver) {
    return (String name) -> resolver.find(name).orElse(null);
  }

  /**
   * Creates an attribute resolver based on registered unknown patterns and resolved attribute
   * values.
   *
   * <p>This provides attribute lookups and unknown matching functions to the interpreter.
   */
  public CelAttributeResolver createAttributeResolver() {
    return DefaultAttributeResolver.create(unresolvedAttributes, resolvedAttributes);
  }

  /** Accessor for the underlying variable resolver. */
  public GlobalResolver variableResolver() {
    return variableResolver;
  }

  /**
   * Creates a new unknown context that is a copy of the current context with the provided
   * additional attribute values.
   *
   * <p>any unknown CelAttributePatterns are removed if they are satisfied by the newly resolved
   * attributes.
   */
  public UnknownContext withResolvedAttributes(Map<CelAttribute, Object> resolvedAttributes) {
    return new UnknownContext(
        this.variableResolver,
        unresolvedAttributes.stream()
            .filter((pattern) -> !patternMaskedByResolvedAttribute(resolvedAttributes, pattern))
            .collect(toImmutableList()),
        ImmutableMap.<CelAttribute, Object>builder()
            .putAll(this.resolvedAttributes)
            .putAll(resolvedAttributes)
            .buildOrThrow());
  }

  private boolean patternMaskedByResolvedAttribute(
      Map<CelAttribute, Object> resolved, CelAttributePattern pattern) {
    return resolved.keySet().stream().anyMatch(pattern::isPartialMatch);
  }

  /**
   * Default implementation of an attribute resolver.
   *
   * <p>Checks against a map of resolved attributes then a list of unknown patterns.
   */
  private static class DefaultAttributeResolver implements CelAttributeResolver {
    private final ImmutableList<CelAttributePattern> unresolvedAttributes;
    private final ImmutableMap<CelAttribute, Object> resolvedAttributes;

    private DefaultAttributeResolver(
        ImmutableList<CelAttributePattern> unresolvedAttributes,
        ImmutableMap<CelAttribute, Object> resolvedAttributes) {
      this.unresolvedAttributes = unresolvedAttributes;
      this.resolvedAttributes = resolvedAttributes;
    }

    private static DefaultAttributeResolver create(
        ImmutableList<CelAttributePattern> attributes,
        ImmutableMap<CelAttribute, Object> resolvedAttributes) {
      return new DefaultAttributeResolver(attributes, resolvedAttributes);
    }

    @Override
    public Optional<Object> resolve(CelAttribute attribute) {
      Object entry = resolvedAttributes.get(attribute);
      if (entry != null) {
        return Optional.of(entry);
      }

      return unresolvedAttributes.stream()
          .filter(pattern -> pattern.isMatch(attribute))
          .findFirst()
          .map(p -> CelUnknownSet.create(p.simplify(attribute)));
    }

    @Override
    public Optional<CelUnknownSet> maybePartialUnknown(CelAttribute attribute) {
      return unresolvedAttributes.stream()
          .filter(p -> p.isPartialMatch(attribute))
          .findFirst()
          .map(p -> CelUnknownSet.create(p.simplify(attribute)));
    }
  }
}
