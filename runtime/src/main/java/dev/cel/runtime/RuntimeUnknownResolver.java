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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.annotations.Internal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime representation of the current state in an iterative evaluation session.
 *
 * <p>This is used by the interpreter to coordinate identifying and tracking unknown values through
 * evaluation.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public class RuntimeUnknownResolver {
  private static final CelAttributeResolver DEFAULT_RESOLVER = new NullAttributeResolver();

  /** The underlying resolver for known values. */
  private final GlobalResolver resolver;

  /** Resolver for unknown and resolved attributes. */
  private final CelAttributeResolver attributeResolver;

  private final boolean attributeTrackingEnabled;

  private RuntimeUnknownResolver(
      GlobalResolver resolver,
      CelAttributeResolver attributeResolver,
      boolean attributeTrackingEnabled) {
    this.resolver = resolver;
    this.attributeResolver = attributeResolver;
    this.attributeTrackingEnabled = attributeTrackingEnabled;
  }

  public static RuntimeUnknownResolver fromResolver(GlobalResolver resolver) {
    // This prevents calculating the attribute trail if it will never be used for
    // efficiency, but doesn't change observable behavior.
    return new RuntimeUnknownResolver(
        resolver, DEFAULT_RESOLVER, /* attributeTrackingEnabled= */ false);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link RuntimeUnknownResolver}. */
  public static class Builder {
    private CelAttributeResolver attributeResolver;
    private GlobalResolver resolver;

    private Builder() {
      resolver = GlobalResolver.EMPTY;
      attributeResolver = DEFAULT_RESOLVER;
    }

    @CanIgnoreReturnValue
    public Builder setResolver(GlobalResolver resolver) {
      this.resolver = resolver;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAttributeResolver(CelAttributeResolver resolver) {
      attributeResolver = resolver;
      return this;
    }

    public RuntimeUnknownResolver build() {
      return new RuntimeUnknownResolver(resolver, attributeResolver, true);
    }
  }

  /**
   * Return a single element unknown set if the attribute is partially unknown based on the defined
   * patterns.
   */
  Optional<AccumulatedUnknowns> maybePartialUnknown(CelAttribute attribute) {
    CelUnknownSet unknownSet = attributeResolver.maybePartialUnknown(attribute).orElse(null);
    return Optional.ofNullable(unknownSet).map(InterpreterUtil::adaptToAccumulatedUnknowns);
  }

  /** Resolve a simple name to a value. */
  DefaultInterpreter.IntermediateResult resolveSimpleName(String name, Long exprId) {
    CelAttribute attr = CelAttribute.EMPTY;

    if (attributeTrackingEnabled) {
      attr = CelAttribute.fromQualifiedIdentifier(name);

      Optional<Object> result = resolveAttribute(attr);
      if (result.isPresent()) {
        return DefaultInterpreter.IntermediateResult.create(attr, result.get());
      }
    }

    Object result = resolver.resolve(name);

    return DefaultInterpreter.IntermediateResult.create(
        attr, InterpreterUtil.valueOrUnknown(result, exprId));
  }

  void cacheLazilyEvaluatedResult(String name, DefaultInterpreter.IntermediateResult result) {
    throw new IllegalStateException(
        "Internal error: Lazy attributes can only be cached in ScopedResolver.");
  }

  void declareLazyAttribute(String attrName) {
    throw new IllegalStateException(
        "Internal error: Lazy attributes can only be declared in ScopedResolver.");
  }

  /**
   * Attempt to resolve an attribute bound to a context variable. This is used to shadow lazily
   * resolved values behind field accesses and index operations.
   */
  Optional<Object> resolveAttribute(CelAttribute attr) {
    Object resolved = attributeResolver.resolve(attr).orElse(null);
    return Optional.ofNullable(resolved).map(InterpreterUtil::maybeAdaptToAccumulatedUnknowns);
  }

  ScopedResolver withScope(Map<String, DefaultInterpreter.IntermediateResult> vars) {
    return new ScopedResolver(this, vars);
  }

  static final class ScopedResolver extends RuntimeUnknownResolver {
    private final RuntimeUnknownResolver parent;
    private final Map<String, DefaultInterpreter.IntermediateResult> shadowedVars;
    private final Map<String, DefaultInterpreter.IntermediateResult> lazyEvalResultCache;

    private ScopedResolver(
        RuntimeUnknownResolver parent,
        Map<String, DefaultInterpreter.IntermediateResult> shadowedVars) {
      super(parent.resolver, parent.attributeResolver, parent.attributeTrackingEnabled);
      this.parent = parent;
      this.shadowedVars = shadowedVars;
      this.lazyEvalResultCache = new HashMap<>();
    }

    @Override
    DefaultInterpreter.IntermediateResult resolveSimpleName(String name, Long exprId) {
      DefaultInterpreter.IntermediateResult result = lazyEvalResultCache.get(name);
      if (result != null) {
        return copyIfMutable(result);
      }
      result = shadowedVars.get(name);
      if (result != null) {
        return result;
      }
      return parent.resolveSimpleName(name, exprId);
    }

    @Override
    void cacheLazilyEvaluatedResult(String name, DefaultInterpreter.IntermediateResult result) {
      // Ensure that lazily evaluated result is stored at the proper scope.
      // A lazily attribute is first declared when a new cel.bind/cel.block expr is encountered.
      //
      // If this attribute isn't found in the current scope, we need to walk up the parent scopes
      // until we find this declaration.
      //
      // For example: cel.bind(x, get_true(), ['foo','bar'].map(unused, x && x))
      //
      // Here, `x` would be evaluated in map macro's scope, but the result should be stored in
      // cel.bind's scope.
      if (!lazyEvalResultCache.containsKey(name)) {
        parent.cacheLazilyEvaluatedResult(name, result);
      } else {
        lazyEvalResultCache.put(name, copyIfMutable(result));
      }
    }

    @Override
    void declareLazyAttribute(String attrName) {
      lazyEvalResultCache.put(attrName, null);
    }

    /**
     * Perform a defensive copy of the intermediate result if it is mutable.
     *
     * <p>Some internal types are mutable to optimize performance, but this can cause issues when
     * the result can be reused in multiple subexpressions due to caching.
     *
     * <p>Note: this is necessary on both the cache put and get path since the interpreter may use
     * the same instance that was cached as a return value.
     */
    private static DefaultInterpreter.IntermediateResult copyIfMutable(
        DefaultInterpreter.IntermediateResult result) {
      if (result.value() instanceof AccumulatedUnknowns) {
        AccumulatedUnknowns unknowns = (AccumulatedUnknowns) result.value();
        return DefaultInterpreter.IntermediateResult.create(
            result.attribute(),
            AccumulatedUnknowns.create(unknowns.exprIds(), unknowns.attributes()));
      }
      return result;
    }
  }

  /** Null implementation for attribute resolution. */
  private static final class NullAttributeResolver implements CelAttributeResolver {
    @Override
    public Optional<Object> resolve(CelAttribute attr) {
      return Optional.empty();
    }

    @Override
    public Optional<CelUnknownSet> maybePartialUnknown(CelAttribute attr) {
      return Optional.empty();
    }
  }
}
