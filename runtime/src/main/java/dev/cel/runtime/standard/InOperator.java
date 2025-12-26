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

import static dev.cel.common.Operator.IN;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Standard function for the ('in') operator. */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class InOperator extends CelStandardFunction {
  private static final InOperator ALL_OVERLOADS = create(InOverload.values());

  public static InOperator create() {
    return ALL_OVERLOADS;
  }

  public static InOperator create(InOperator.InOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static InOperator create(Iterable<InOperator.InOverload> overloads) {
    return new InOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum InOverload implements CelStandardOverload {
    IN_LIST(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "in_list",
                Object.class,
                List.class,
                (Object value, List list) -> runtimeEquality.inList(list, value))),
    IN_MAP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "in_map",
                Object.class,
                Map.class,
                (Object key, Map map) -> runtimeEquality.inMap(map, key)));

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    InOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private InOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(IN.getFunction(), overloads);
  }
}
