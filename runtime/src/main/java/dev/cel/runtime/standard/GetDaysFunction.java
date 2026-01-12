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
import com.google.protobuf.Duration;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code getDays}. */
public final class GetDaysFunction extends CelStandardFunction {
  private static final GetDaysFunction ALL_OVERLOADS = create(GetDaysOverload.values());

  public static GetDaysFunction create() {
    return ALL_OVERLOADS;
  }

  public static GetDaysFunction create(GetDaysFunction.GetDaysOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static GetDaysFunction create(Iterable<GetDaysFunction.GetDaysOverload> overloads) {
    return new GetDaysFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum GetDaysOverload implements CelStandardOverload {
    DURATION_TO_DAYS(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "duration_to_days", java.time.Duration.class, java.time.Duration::toDays);
          } else {
            return CelFunctionBinding.from(
                "duration_to_days", Duration.class, ProtoTimeUtils::toDays);
          }
        }),
    ;

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    GetDaysOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private GetDaysFunction(ImmutableSet<CelStandardOverload> overloads) {
    super("getDays", overloads);
  }
}
