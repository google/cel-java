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

package dev.cel.runtime.standard;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Standard function for optional support. Note that users must also add {@code CelOptionalLibrary}
 * as a runtime library to take advantage of CEL optionals.
 *
 * <p>TODO: Move into CelOptionalLibrary
 */
public final class OptionalFunction extends CelStandardFunction {
  private static final OptionalFunction ALL_OVERLOADS = create(OptionalOverload.values());

  public static OptionalFunction create() {
    return ALL_OVERLOADS;
  }

  public static OptionalFunction create(OptionalFunction.OptionalOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static OptionalFunction create(Iterable<OptionalFunction.OptionalOverload> overloads) {
    return new OptionalFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum OptionalOverload implements CelStandardOverload {
    SELECT_OPTIONAL_FIELD(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "select_optional_field", // This only handles map selection. Proto selection is
                // special cased inside the interpreter.
                Map.class,
                String.class,
                runtimeEquality::findInMap)),
    MAP_OPTINDEX_OPTIONAL_VALUE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "map_optindex_optional_value",
                Map.class,
                Object.class,
                runtimeEquality::findInMap)),

    @SuppressWarnings("rawtypes")
    OPTIONAL_MAP_OPTINDEX_OPTIONAL_VALUE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "optional_map_optindex_optional_value",
                Optional.class,
                Object.class,
                (Optional optionalMap, Object key) ->
                    indexOptionalMap(optionalMap, key, runtimeEquality))),

    @SuppressWarnings("rawtypes")
    OPTIONAL_MAP_INDEX_VALUE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "optional_map_index_value",
                Optional.class,
                Object.class,
                (Optional optionalMap, Object key) ->
                    indexOptionalMap(optionalMap, key, runtimeEquality))),
    OPTIONAL_LIST_INDEX_INT(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "optional_list_index_int",
                Optional.class,
                Long.class,
                OptionalFunction::indexOptionalList)),
    @SuppressWarnings("rawtypes")
    LIST_OPTINDEX_OPTIONAL_INT(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "list_optindex_optional_int",
                List.class,
                Long.class,
                (List list, Long index) -> {
                  int castIndex = Ints.checkedCast(index);
                  if (castIndex < 0 || castIndex >= list.size()) {
                    return Optional.empty();
                  }
                  return Optional.of(list.get(castIndex));
                })),
    OPTIONAL_LIST_OPTINDEX_OPTIONAL_INT(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "optional_list_optindex_optional_int",
                Optional.class,
                Long.class,
                OptionalFunction::indexOptionalList));

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    OptionalOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private static Object indexOptionalMap(
      Optional<?> optionalMap, Object key, RuntimeEquality runtimeEquality) {
    if (!optionalMap.isPresent()) {
      return Optional.empty();
    }

    Map<?, ?> map = (Map<?, ?>) optionalMap.get();

    return runtimeEquality.findInMap(map, key);
  }

  private static Object indexOptionalList(Optional<?> optionalList, long index) {
    if (!optionalList.isPresent()) {
      return Optional.empty();
    }
    List<?> list = (List<?>) optionalList.get();
    int castIndex = Ints.checkedCast(index);
    if (castIndex < 0 || castIndex >= list.size()) {
      return Optional.empty();
    }
    return Optional.of(list.get(castIndex));
  }

  private OptionalFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
