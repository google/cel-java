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
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Standard function for the indexing ({@code list[0] or map['foo']}) operator */
public final class IndexOperator extends CelStandardFunction {

  public static IndexOperator create(IndexOperator.IndexOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static IndexOperator create(Iterable<IndexOperator.IndexOverload> overloads) {
    return new IndexOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  @SuppressWarnings({"unchecked"})
  public enum IndexOverload implements CelStandardOverload {
    INDEX_LIST(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "index_list", List.class, Number.class, RuntimeHelpers::indexList)),
    INDEX_MAP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "index_map", Map.class, Object.class, runtimeEquality::indexMap));

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    IndexOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private IndexOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
