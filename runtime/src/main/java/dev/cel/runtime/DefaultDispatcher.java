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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Default implementation of dispatcher. */
@Immutable
final class DefaultDispatcher implements CelFunctionResolver {

  private final ImmutableMap<String, CelResolvedOverload> overloads;

  @Override
  public Optional<CelResolvedOverload> findOverloadMatchingArgs(
      String functionName, List<String> overloadIds, Object[] args) throws CelEvaluationException {
    return findOverloadMatchingArgs(functionName, overloadIds, overloads, args);
  }

  /** Finds the overload that matches the given function name, overload IDs, and arguments. */
  static Optional<CelResolvedOverload> findOverloadMatchingArgs(
      String functionName,
      List<String> overloadIds,
      Map<String, ? extends CelResolvedOverload> overloads,
      Object[] args)
      throws CelEvaluationException {
    int matchingOverloadCount = 0;
    CelResolvedOverload match = null;
    List<String> candidates = null;
    for (String overloadId : overloadIds) {
      CelResolvedOverload overload = overloads.get(overloadId);
      // If the overload is null, it means that the function was not registered; however, it is
      // possible that the overload refers to a late-bound function.
      if (overload != null && overload.canHandle(args)) {
        if (++matchingOverloadCount > 1) {
          if (candidates == null) {
            candidates = new ArrayList<>();
            candidates.add(match.getOverloadId());
          }
          candidates.add(overloadId);
        }
        match = overload;
      }
    }

    if (matchingOverloadCount > 1) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "Ambiguous overloads for function '%s'. Matching candidates: %s",
              functionName, Joiner.on(", ").join(candidates))
          .setErrorCode(CelErrorCode.AMBIGUOUS_OVERLOAD)
          .build();
    }
    return Optional.ofNullable(match);
  }

  /**
   * Finds the single registered overload iff it's marked as a non-strict function.
   *
   * <p>The intent behind this function is to provide an at-parity behavior with existing
   * DefaultInterpreter, where it historically special-cased locating a single overload for certain
   * non-strict functions, such as not_strictly_false. This method should not be used outside of
   * this specific context.
   *
   * @throws IllegalStateException if there are multiple overloads that are marked non-strict.
   */
  Optional<CelResolvedOverload> findSingleNonStrictOverload(List<String> overloadIds) {
    for (String overloadId : overloadIds) {
      CelResolvedOverload overload = overloads.get(overloadId);
      if (overload != null && !overload.isStrict()) {
        if (overloadIds.size() > 1) {
          throw new IllegalStateException(
              String.format(
                  "%d overloads found for a non-strict function. Expected 1.", overloadIds.size()));
        }
        return Optional.of(overload);
      }
    }

    return Optional.empty();
  }

  static Builder newBuilder() {
    return new AutoBuilder_DefaultDispatcher_Builder();
  }

  @AutoBuilder(ofClass = DefaultDispatcher.class)
  abstract static class Builder {

    abstract ImmutableMap<String, CelResolvedOverload> overloads();

    abstract ImmutableMap.Builder<String, CelResolvedOverload> overloadsBuilder();

    @CanIgnoreReturnValue
    Builder addOverload(
        String overloadId,
        List<Class<?>> argTypes,
        boolean isStrict,
        CelFunctionOverload overload) {
      checkNotNull(overloadId);
      checkNotNull(argTypes);
      checkNotNull(overload);

      overloadsBuilder()
          .put(overloadId, CelResolvedOverload.of(overloadId, overload, isStrict, argTypes));
      return this;
    }

    abstract DefaultDispatcher build();
  }

  DefaultDispatcher(ImmutableMap<String, CelResolvedOverload> overloads) {
    this.overloads = overloads;
  }
}
