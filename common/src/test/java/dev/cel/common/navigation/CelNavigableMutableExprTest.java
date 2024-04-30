// Copyright 2024 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExpr.CelMutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelNavigableMutableExprTest {

  @Test
  public void construct_withoutParent() {
    CelMutableExpr constExpr = CelMutableExpr.ofConstant(1, CelConstant.ofValue("test"));

    CelNavigableMutableExpr navigableExpr =
        CelNavigableMutableExpr.builder().setExpr(constExpr).setDepth(2).build();

    assertThat(navigableExpr.expr()).isEqualTo(constExpr);
    assertThat(navigableExpr.depth()).isEqualTo(2);
    assertThat(navigableExpr.parent()).isEmpty();
  }

  @Test
  public void construct_withParent() {
    CelMutableExpr constExpr = CelMutableExpr.ofConstant(1, CelConstant.ofValue("test"));
    CelMutableExpr identExpr = CelMutableExpr.ofIdent(2, "a");

    CelNavigableMutableExpr parentExpr =
        CelNavigableMutableExpr.builder().setExpr(identExpr).setDepth(1).build();
    CelNavigableMutableExpr navigableExpr =
        CelNavigableMutableExpr.builder()
            .setExpr(constExpr)
            .setDepth(2)
            .setParent(parentExpr)
            .build();

    assertThat(parentExpr.expr()).isEqualTo(identExpr);
    assertThat(parentExpr.depth()).isEqualTo(1);
    assertThat(parentExpr.parent()).isEmpty();
    assertThat(navigableExpr.expr()).isEqualTo(constExpr);
    assertThat(navigableExpr.depth()).isEqualTo(2);
    assertThat(navigableExpr.parent()).hasValue(parentExpr);
  }

  @Test
  public void builderFromInstance_sameAsStaticBuilder() {
    CelNavigableMutableExpr.Builder staticBuilder =
        CelNavigableMutableExpr.builder().setExpr(CelMutableExpr.ofNotSet());

    CelNavigableMutableExpr.Builder builderFromInstance =
        CelNavigableMutableExpr.fromExpr(CelMutableExpr.ofNotSet())
            .builderFromInstance()
            .setExpr(CelMutableExpr.ofNotSet());

    assertThat(staticBuilder.build()).isEqualTo(builderFromInstance.build());
  }

  @Test
  public void allNodes_filteredConstants_returnsAllConstants() {
    CelNavigableMutableExpr mutableExpr =
        CelNavigableMutableExpr.fromExpr(
            CelMutableExpr.ofList(
                CelMutableList.create(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                    CelMutableExpr.ofList(
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))))));

    ImmutableList<CelMutableExpr> allNodes =
        mutableExpr
            .allNodes()
            .filter(node -> node.getKind().equals(Kind.CONSTANT))
            .map(BaseNavigableExpr::expr)
            .collect(toImmutableList());

    assertThat(allNodes)
        .containsExactly(
            CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
            CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))
        .inOrder();
  }

  @Test
  public void descendants_filteredConstants_returnsAllConstants() {
    CelNavigableMutableExpr mutableExpr =
        CelNavigableMutableExpr.fromExpr(
            CelMutableExpr.ofList(
                CelMutableList.create(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                    CelMutableExpr.ofList(
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))))));

    ImmutableList<CelMutableExpr> allNodes =
        mutableExpr
            .descendants()
            .filter(node -> node.getKind().equals(Kind.CONSTANT))
            .map(BaseNavigableExpr::expr)
            .collect(toImmutableList());

    assertThat(allNodes)
        .containsExactly(
            CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
            CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))
        .inOrder();
  }

  @Test
  public void children_filteredConstants_returnsSingleConstant() {
    CelNavigableMutableExpr mutableExpr =
        CelNavigableMutableExpr.fromExpr(
            CelMutableExpr.ofList(
                CelMutableList.create(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                    CelMutableExpr.ofList(
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))))));

    ImmutableList<CelMutableExpr> allNodes =
        mutableExpr
            .children()
            .filter(node -> node.getKind().equals(Kind.CONSTANT))
            .map(BaseNavigableExpr::expr)
            .collect(toImmutableList());

    assertThat(allNodes)
        .containsExactly(CelMutableExpr.ofConstant(CelConstant.ofValue("element1")));
  }
}
