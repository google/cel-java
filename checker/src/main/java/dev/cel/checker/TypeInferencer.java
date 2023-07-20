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

package dev.cel.checker;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * The TypeInferencer provides methods aligned with type unification algorithms like Hindley-Milner.
 *
 * <p>The core functions are pretty simple conceptually:
 *
 * <ul>
 *   <li>{@code unify} - unify the type representations for two or more types
 *   <li>{@code isAssignable} - determine whether one set of types is assignable to another set.
 *   <li>{@code specialize} - perform type-substitution on the type filling in values stored in
 *       recorded substitutions.
 * </ul>
 *
 * <p>The assignability check is most commonly used for determining whether a pair of arguments are
 * compatible with one another. This is mostly used for function calls. The {@code unify}, however,
 * is mostly used for literal construction where the resolved type must be the most accurate
 * combination of the user-specified types (assuming they yield a supported type).
 *
 * <p>When either of the {@code isAssignable} or {@code unify} methods are called, each method will
 * minimally return the set of type substitutions made during the call. These substituation maps may
 * be recorded into the {@code TypeInferencer} so they may be used with future type resolutions.
 * Whether to record the type substitutions depends greatly on the context in which the calls are
 * made.
 */
@CheckReturnValue
final class TypeInferencer {

  private final Map<String, CelType> substitutions;
  private final ImmutableList<CelType> unionTypes;

  // Consider making these constructors public but exposed via a visibility restriction

  /**
   * Construct a new {@code TypeInferencer} with a list of {@code unionTypes}.
   *
   * <p>The {@code unionTypes} must be ordered by least to most general, with the last union type
   * being a top-type (if so desired).
   */
  TypeInferencer(ImmutableList<CelType> unionTypes) {
    this(unionTypes, ImmutableMap.of());
  }

  /**
   * Construct a new {@code TypeInferencer} with a list of {@code unionTypes} and a set of {@code
   * priorSubstitutions}.
   *
   * <p>The {@code unionTypes} must be ordered by least to most general, with the last union type
   * being a top-type (if so desired).
   *
   * <p>The {@code priorSubstitutions} may be type substitutions that occurred within an earlier
   * type inference, or simply a set of type mappings to preserve between checks.
   */
  TypeInferencer(ImmutableList<CelType> unionTypes, Map<String, CelType> priorSubstitutions) {
    this.unionTypes = unionTypes;
    this.substitutions = new HashMap<>();
    this.substitutions.putAll(priorSubstitutions);
  }

  // Helper interface to aide in type unification.
  @FunctionalInterface
  private interface TypeSelectionStrategy {
    Optional<CelType> selectType(CelType type1, CelType type2);
  }

  /**
   * Perform type substitution on the {@code type} using the recorded substitution set, replacing
   * unresolved type params with the input {@code topType}.
   */
  CelType finalize(CelType type, CelType topType) {
    return type.kind().isTypeParam()
        ? Optional.ofNullable(substitutions.get(type.name())).map(this::specialize).orElse(topType)
        : finalizeParameters(type, topType);
  }

  /**
   * Perform type substitution on the {@code type} using the recorded substitution set, leaving
   * unresolved type params intact.
   */
  CelType specialize(CelType type) {
    return type.kind().isTypeParam()
        ? Optional.ofNullable(substitutions.get(type.name())).map(this::specialize).orElse(type)
        : specializeParameters(type);
  }

  /** Determine whether {@code type} is assignable to {@code desiredType}. */
  Optional<UnificationResult> unify(CelType type, CelType desiredType) {
    // Check how this behaves with `type` values.
    return unifier(substitutions).compute(type, desiredType);
  }

  /** Unify a list of {@code types} together into a single {@code CelType}. */
  Optional<UnificationResult> unify(List<CelType> types, CelType desiredType) {
    CelType unifiedType = desiredType;
    UnificationComputation unification = unifier(substitutions);
    for (CelType type : types) {
      Optional<UnificationResult> result = unification.compute(type, unifiedType);
      if (!result.isPresent()) {
        return Optional.empty();
      }
      // If the desired type is a type param, then it would have had a substitution recorded
      // in the UnificationComputation.
      if (!desiredType.kind().isTypeParam()) {
        unifiedType = result.get().unifiedType().get();
      }
    }
    if (unifiedType.kind().isTypeParam()) {
      unifiedType = unification.substitutions.get(unifiedType.name());
      return Optional.of(
          unificationResult(unifiedType, ImmutableMap.of(unifiedType.name(), unifiedType)));
    }
    return Optional.of(unificationResult(unifiedType));
  }

  /** Determine whether the {@code types} are assignable to the {@code targetTypes}. */
  Optional<ImmutableMap<String, CelType>> isAssignable(
      List<CelType> types, List<CelType> targetTypes) {
    if (types.size() != targetTypes.size()) {
      return Optional.empty();
    }
    Map<String, CelType> intermediateSubs = new HashMap<>();
    UnificationComputation unification = unifier(substitutions, this::mostGeneralType);
    for (int i = 0; i < types.size(); i++) {
      CelType type = types.get(i);
      CelType desiredType = targetTypes.get(i);
      Optional<UnificationResult> result = unification.compute(type, desiredType);
      if (!result.isPresent()) {
        return Optional.empty();
      }
      intermediateSubs.putAll(result.get().substitutions());
    }
    return Optional.of(ImmutableMap.copyOf(intermediateSubs));
  }

  /**
   * Record a set of {@code newSubstitutions} in the {@code TypeInferencer}.
   *
   * <p>When there are duplicate substitutions for the same type param, the most general type param
   * type is used as the substitution value.
   */
  void recordSubstitutions(Map<String, CelType> newSubstitutions) {
    // It's possible that type merging is not desired in this circumstance, but the most general
    // type should be used as long as it is properly identified earlier in evaluation.
    substitutions.putAll(mergeSubstitutions(substitutions, newSubstitutions, this::mergeTypes));
  }

  private UnificationComputation unifier(Map<String, CelType> substitutions) {
    return unifier(substitutions, this::mergeTypes);
  }

  private UnificationComputation unifier(
      Map<String, CelType> substitutions, TypeSelectionStrategy mergeStrategy) {
    return new UnificationComputation(substitutions, mergeStrategy, this::mostGeneralType);
  }

  // Strategy for determining which is the most general type without actually expanding or altering
  // the type definition in the result.
  private Optional<CelType> mostGeneralType(CelType type1, CelType type2) {
    // Error types should always be returned as errors.
    if (type1.kind().isError() || type2.kind().isError()) {
      return Optional.of(SimpleType.ERROR);
    }
    // If the types are assignable then return the second type, the later type.
    if (type2.isAssignableFrom(type1)) {
      return Optional.of(type2);
    }
    // If the types are assignable then return the second type.
    if (type1.isAssignableFrom(type2)) {
      return Optional.of(type1);
    }
    // Type parameters don't need to be unified since they will have been unified
    // as part of the assignability computation.
    return Optional.empty();
  }

  // Strategy for determining which is the most general type which may alter the type by promoting
  // the types into the narrowest union type which can contain the referenced types.
  private Optional<CelType> mergeTypes(CelType type1, CelType type2) {
    // Default to returning the general type if one is found.
    Optional<CelType> generalType = mostGeneralType(type1, type2);
    if (generalType.isPresent()) {
      return generalType;
    }

    // Otherwise search the union types in order from narrowest to broadest to determine
    // whether any of the union types can represent both values.
    return unionTypes.stream()
        .filter(ut -> ut.isAssignableFrom(type1) && ut.isAssignableFrom(type2))
        .findFirst();
  }

  private CelType finalizeParameters(CelType type, CelType topType) {
    ImmutableList<CelType> params = type.parameters();
    return type.withParameters(
        params.stream().map(p -> finalize(p, topType)).collect(toImmutableList()));
  }

  private CelType specializeParameters(CelType type) {
    ImmutableList<CelType> params = type.parameters();
    return type.withParameters(params.stream().map(this::specialize).collect(toImmutableList()));
  }

  // Merge the prior and current substitutions based on the
  private static ImmutableMap<String, CelType> mergeSubstitutions(
      Map<String, CelType> prevSubstitutions,
      Map<String, CelType> nextSubstitutions,
      TypeSelectionStrategy mergeTypes) {
    Map<String, CelType> updated = new HashMap<>();
    updated.putAll(prevSubstitutions);
    for (Map.Entry<String, CelType> subEntry : nextSubstitutions.entrySet()) {
      CelType sub = subEntry.getValue();
      CelType existingSub = updated.get(subEntry.getKey());
      if (existingSub != null) {
        sub =
            mergeTypes
                .selectType(existingSub, sub)
                .orElseThrow(() -> new NoSuchElementException("No value present"));
      }
      updated.put(subEntry.getKey(), sub);
    }
    return ImmutableMap.copyOf(updated);
  }

  private static UnificationResult unificationResult(CelType unifiedType) {
    return unificationResult(unifiedType, ImmutableMap.of());
  }

  private static UnificationResult unificationResult(
      CelType unifiedType, ImmutableMap<String, CelType> substitutions) {
    return new AutoValue_TypeInferencer_UnificationResult(Optional.of(unifiedType), substitutions);
  }

  /**
   * {@code UnificationResult} indicates the {@code unifiedType} as well as a map of substitutions
   * performed during type unification.
   */
  @AutoValue
  abstract static class UnificationResult {

    abstract Optional<CelType> unifiedType();

    abstract ImmutableMap<String, CelType> substitutions();
  }

  private static final class UnificationComputation {

    private final Map<String, CelType> substitutions;

    private final TypeSelectionStrategy unifyTypes;

    private final TypeSelectionStrategy mostGeneralType;

    private UnificationComputation(
        Map<String, CelType> substitutions,
        TypeSelectionStrategy unifyTypes,
        TypeSelectionStrategy mostGeneralType) {
      this.substitutions = new HashMap<>();
      this.substitutions.putAll(substitutions);
      this.unifyTypes = unifyTypes;
      this.mostGeneralType = mostGeneralType;
    }

    private Optional<UnificationResult> compute(CelType type, CelType desiredType) {
      // First attempt type substitution for type-params.
      if (desiredType.kind().isTypeParam()) {
        return computeTypeParam(type, desiredType);
      }
      if (type.kind().isTypeParam()) {
        return computeTypeParam(desiredType, type);
      }

      // Pair-wise compute assignability for type parameters embedded within nearly equivalent types
      ImmutableMap<String, CelType> parameterSubstitutions = ImmutableMap.of();
      if (type.kind() == desiredType.kind()
          && type.name().equals(desiredType.name())
          && type.parameters().size() == desiredType.parameters().size()) {
        ImmutableList.Builder<CelType> unifiedParams = ImmutableList.builder();
        ImmutableList<CelType> typeParameters = type.parameters();
        ImmutableList<CelType> desiredTypeParameters = desiredType.parameters();
        for (int i = 0; i < typeParameters.size(); i++) {
          Optional<UnificationResult> result =
              compute(typeParameters.get(i), desiredTypeParameters.get(i));
          // The intermediate comptutation indicates that type cannot be unified.
          if (!result.isPresent()) {
            return Optional.empty();
          }
          unifiedParams.add(result.get().unifiedType().get());
          parameterSubstitutions =
              mergeSubstitutions(parameterSubstitutions, result.get().substitutions(), unifyTypes);
        }
        ImmutableList<CelType> updatedParams = unifiedParams.build();
        type = type.withParameters(updatedParams);
        desiredType = desiredType.withParameters(updatedParams);
      }

      // When the type assignability has been computed, if the types are assignable, return the most
      // general type according to the type selection strategy provided. If the types cannot be
      // assigned, return the top-type if one has been provided.
      ImmutableMap<String, CelType> parameterSubstitutionSet = parameterSubstitutions;
      Optional<CelType> selectedType = mostGeneralType.selectType(type, desiredType);
      return selectedType.map(typeVal -> unificationResult(typeVal, parameterSubstitutionSet));
    }

    private Optional<UnificationResult> computeTypeParam(CelType type, CelType typeParam) {
      Optional<CelType> typeParamSub = findSubstitution(typeParam.name());
      // Update the type param mapping, if possible, otherwise return not assignable.
      if (typeParamSub.isPresent()) {
        UnificationComputation unification =
            new UnificationComputation(substitutions, unifyTypes, unifyTypes);
        Optional<UnificationResult> result = unification.compute(type, typeParamSub.get());
        // This indicates that the prior type substitution was not compatible with the current type.
        if (!result.isPresent()) {
          return Optional.empty();
        }
        Optional<CelType> unifiedType = result.get().unifiedType();
        if (unifiedType.isPresent()) {
          CelType typeVal = unifiedType.get();
          substitutions.put(typeParam.name(), typeVal);
          return Optional.of(
              unificationResult(typeVal, ImmutableMap.of(typeParam.name(), typeVal)));
        }
      }
      // Before recording a substitution, make sure that the type parameter is not part of the
      // target type's definition.
      if (!referencedIn(typeParam.name(), type)) {
        substitutions.put(typeParam.name(), type);
        return Optional.of(unificationResult(type, ImmutableMap.of(typeParam.name(), type)));
      }
      return Optional.empty();
    }

    // This is the 'occurs' check for a type parameter name within a given type. This check will
    // also search all 'parameters' of the 'targetType'.
    private boolean referencedIn(String typeName, CelType targetType) {
      // Checking whether the type param is referenced in the other type means that the type
      // parameter appears somewhere within the parameterized types of the 'targetType' argument.
      if ((targetType.kind().isTypeParam() && targetType.name().equals(typeName))
          || referencedIn(typeName, targetType.parameters())) {
        return true;
      }
      // Also ensure that the typeName is not referenced within any substitutions for the
      // 'targetType' either.
      return targetType.kind().isTypeParam()
          && findSubstitution(targetType.name())
              .filter(sub -> referencedIn(typeName, sub))
              .isPresent();
    }

    // This is the 'occurs' check which searches a list of types for the type parameter name.
    private boolean referencedIn(String typeName, ImmutableList<CelType> typeSet) {
      return typeSet.stream().anyMatch(t -> referencedIn(typeName, t));
    }

    private Optional<CelType> findSubstitution(String typeName) {
      return Optional.ofNullable(substitutions.get(typeName));
    }
  }
}
