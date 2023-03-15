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

package dev.cel.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelOptionsTest {

  @Test
  public void current_success_celOptions() {
    CelOptions options = CelOptions.current().build();
    assertThat(options).isNotNull();
    assertThat(options.enableRegexPartialMatch()).isTrue();
  }

  @Test
  public void fromExprFeatures_success_allRoundtrip() {
    ImmutableSet.Builder<ExprFeatures> allFeatures = ImmutableSet.builder();
    allFeatures.add(
        ExprFeatures.COMPILE_TIME_OVERLOAD_RESOLUTION,
        ExprFeatures.HOMOGENEOUS_LITERALS,
        ExprFeatures.REGEX_PARTIAL_MATCH,
        ExprFeatures.RESERVED_IDS,
        ExprFeatures.RETAIN_REPEATED_UNARY_OPERATORS,
        ExprFeatures.RETAIN_UNBALANCED_LOGICAL_EXPRESSIONS,
        ExprFeatures.UNSIGNED_COMPARISON_AND_ARITHMETIC_IS_UNSIGNED,
        ExprFeatures.ERROR_ON_WRAP,
        ExprFeatures.ERROR_ON_DUPLICATE_KEYS,
        ExprFeatures.POPULATE_MACRO_CALLS,
        ExprFeatures.ENABLE_TIMESTAMP_EPOCH,
        ExprFeatures.ENABLE_HETEROGENEOUS_NUMERIC_COMPARISONS,
        ExprFeatures.ENABLE_UNSIGNED_LONGS,
        ExprFeatures.PROTO_DIFFERENCER_EQUALITY);
    assertThat(CelOptions.fromExprFeatures(allFeatures.build()).toExprFeatures())
        .containsExactlyElementsIn(allFeatures.build());
  }

  @Test
  public void toExprFeatures_success_includesExprFeaturesCurrent() {
    assertThat(CelOptions.current().build().toExprFeatures()).isEqualTo(ExprFeatures.CURRENT);
  }

  @Test
  public void fromExprFeatures_success_currentRoundtrip() {
    assertThat(CelOptions.fromExprFeatures(ExprFeatures.CURRENT).toExprFeatures())
        .isEqualTo(ExprFeatures.CURRENT);
  }

  @Test
  public void fromExprFeatures_success_legacyRoundtrip() {
    assertThat(CelOptions.fromExprFeatures(ExprFeatures.LEGACY).toExprFeatures())
        .isEqualTo(ExprFeatures.LEGACY);
  }

  @Test
  public void current_defaults() {
    // Defaults that aren't represented in deprecated ExprFeatures.
    assertThat(CelOptions.current().build().enableUnknownTracking()).isFalse();
    assertThat(CelOptions.current().build().resolveTypeDependencies()).isTrue();
  }
}
