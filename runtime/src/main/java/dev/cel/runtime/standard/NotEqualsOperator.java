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
import java.util.Arrays;

/** Standard function for the not equals (!=) operator. */
public final class NotEqualsOperator extends CelStandardFunction {
  private static final NotEqualsOperator ALL_OVERLOADS = create(NotEqualsOverload.values());

  public static NotEqualsOperator create() {
    return ALL_OVERLOADS;
  }

  public static NotEqualsOperator create(NotEqualsOperator.NotEqualsOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static NotEqualsOperator create(Iterable<NotEqualsOperator.NotEqualsOverload> overloads) {
    return new NotEqualsOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum NotEqualsOverload implements CelStandardOverload {
    NOT_EQUALS(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "not_equals",
                Object.class,
                Object.class,
                (Object x, Object y) -> !runtimeEquality.objectEquals(x, y)));

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    NotEqualsOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private NotEqualsOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
