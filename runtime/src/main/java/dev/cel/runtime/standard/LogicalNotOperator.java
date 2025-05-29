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

/** Standard function for the logical not (!=) operator. */
public final class LogicalNotOperator extends CelStandardFunction {

  public static LogicalNotOperator create(LogicalNotOperator.LogicalNotOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static LogicalNotOperator create(
      Iterable<LogicalNotOperator.LogicalNotOverload> overloads) {
    return new LogicalNotOperator(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum LogicalNotOverload implements CelStandardOverload {
    LOGICAL_NOT(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("logical_not", Boolean.class, (Boolean x) -> !x));

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    LogicalNotOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private LogicalNotOperator(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
