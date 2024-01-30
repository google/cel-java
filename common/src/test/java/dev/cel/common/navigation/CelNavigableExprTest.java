// Copyright 2023 Google LLC
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

package dev.cel.common.navigation;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Truth8;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelNavigableExprTest {

  @Test
  public void construct_withoutParent_success() {
    CelExpr constExpr = CelExpr.ofConstantExpr(1, CelConstant.ofValue("test"));
    CelNavigableExpr navigableExpr =
        CelNavigableExpr.builder().setExpr(constExpr).setDepth(2).build();

    assertThat(navigableExpr.expr()).isEqualTo(constExpr);
    assertThat(navigableExpr.depth()).isEqualTo(2);
    Truth8.assertThat(navigableExpr.parent()).isEmpty();
  }

  @Test
  public void construct_withParent_success() {
    CelExpr constExpr = CelExpr.ofConstantExpr(1, CelConstant.ofValue("test"));
    CelExpr identExpr = CelExpr.ofIdentExpr(2, "a");
    CelNavigableExpr parentExpr = CelNavigableExpr.builder().setExpr(identExpr).setDepth(1).build();
    CelNavigableExpr navigableExpr =
        CelNavigableExpr.builder().setExpr(constExpr).setDepth(2).setParent(parentExpr).build();

    assertThat(parentExpr.expr()).isEqualTo(identExpr);
    assertThat(parentExpr.depth()).isEqualTo(1);
    Truth8.assertThat(parentExpr.parent()).isEmpty();
    assertThat(navigableExpr.expr()).isEqualTo(constExpr);
    assertThat(navigableExpr.depth()).isEqualTo(2);
    Truth8.assertThat(navigableExpr.parent()).hasValue(parentExpr);
  }
}
