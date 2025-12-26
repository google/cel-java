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

import static dev.cel.common.Operator.EQUALS;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for the equals (=) operator. */
public final class EqualsOperator extends CelStandardFunction {
  private static final EqualsOperator ALL_OVERLOADS = create(EqualsOverload.values());

  public static EqualsOperator create() {
    return ALL_OVERLOADS;
  }

  public static EqualsOperator create(EqualsOperator.EqualsOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static EqualsOperator create(Iterable<EqualsOperator.EqualsOverload> overloads) {
    return new EqualsOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum EqualsOverload implements CelStandardOverload {
    EQUALS(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "equals", Object.class, Object.class, runtimeEquality::objectEquals)),
    ;

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    EqualsOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private EqualsOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(EQUALS.getFunction(), overloads);
  }
}
