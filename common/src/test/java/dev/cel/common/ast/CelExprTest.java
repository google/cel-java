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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelExprTest {

  @Test
  public void celExprBuilder_default() {
    CelExpr expr = CelExpr.newBuilder().build();

    assertThat(expr.id()).isEqualTo(0);
    assertThat(expr.exprKind().getKind()).isEqualTo(Kind.NOT_SET);
  }

  @Test
  public void celExprBuilder_exprIdSet() {
    CelExpr expr = CelExpr.newBuilder().setId(10).build();

    assertThat(expr.id()).isEqualTo(10);
  }

  private enum BuilderExprKindTestCase {
    CONSTANT(CelExpr.newBuilder().setConstant(CelConstant.ofValue(1L)).build(), Kind.CONSTANT),
    IDENT(
        CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName("ident").build()).build(),
        Kind.IDENT),
    CALL(
        CelExpr.newBuilder().setCall(CelCall.newBuilder().setFunction("function").build()).build(),
        Kind.CALL),
    SELECT(
        CelExpr.newBuilder()
            .setSelect(
                CelSelect.newBuilder()
                    .setField("field")
                    .setOperand(CelExpr.newBuilder().build())
                    .build())
            .build(),
        Kind.SELECT),
    CREATE_LIST(
        CelExpr.newBuilder().setCreateList(CelCreateList.newBuilder().build()).build(),
        Kind.CREATE_LIST),
    CREATE_STRUCT(
        CelExpr.newBuilder().setCreateStruct(CelCreateStruct.newBuilder().build()).build(),
        Kind.CREATE_STRUCT),
    COMPREHENSION(
        CelExpr.newBuilder()
            .setComprehension(
                CelComprehension.newBuilder()
                    .setIterVar("iterVar")
                    .setIterRange(CelExpr.newBuilder().build())
                    .setAccuVar("accuVar")
                    .setAccuInit(CelExpr.newBuilder().build())
                    .setLoopCondition(CelExpr.newBuilder().build())
                    .setLoopStep(CelExpr.newBuilder().build())
                    .setResult(CelExpr.newBuilder().build())
                    .build())
            .build(),
        Kind.COMPREHENSION);

    private final CelExpr expr;
    private final ExprKind.Kind expectedExprKind;

    BuilderExprKindTestCase(CelExpr expr, ExprKind.Kind expectedExprKind) {
      this.expr = expr;
      this.expectedExprKind = expectedExprKind;
    }
  }

  @Test
  public void celExprBuilder_exprKindSet(@TestParameter BuilderExprKindTestCase builderTestCase) {
    assertThat(builderTestCase.expr.exprKind().getKind())
        .isEqualTo(builderTestCase.expectedExprKind);
  }

  @Test
  public void celExprBuilder_setConstant() {
    CelConstant celConstant = CelConstant.ofValue("hello");
    CelExpr celExpr = CelExpr.newBuilder().setConstant(celConstant).build();

    assertThat(celExpr.constant()).isEqualTo(celConstant);
  }

  @Test
  public void celExprBuilder_setIdent() {
    CelIdent celIdent = CelIdent.newBuilder().setName("ident").build();
    CelExpr celExpr = CelExpr.newBuilder().setIdent(celIdent).build();

    assertThat(celExpr.ident()).isEqualTo(celIdent);
  }

  @Test
  public void celExprBuilder_setCall() {
    CelCall celCall =
        CelCall.newBuilder()
            .setFunction("function")
            .setTarget(CelExpr.newBuilder().build())
            .build();
    CelExpr celExpr = CelExpr.newBuilder().setCall(celCall).build();

    assertThat(celExpr.call()).isEqualTo(celCall);
  }

  @Test
  public void celExprBuilder_setCall_clearTarget() {
    CelCall celCall =
        CelCall.newBuilder()
            .setFunction("function")
            .setTarget(CelExpr.ofConstantExpr(1, CelConstant.ofValue("test")))
            .build();
    CelExpr celExpr =
        CelExpr.newBuilder().setCall(celCall.toBuilder().clearTarget().build()).build();

    assertThat(celExpr.call()).isEqualTo(CelCall.newBuilder().setFunction("function").build());
  }

  @Test
  public void celExprBuilder_setCall_setArgByIndex() {
    CelCall celCall =
        CelCall.newBuilder()
            .setFunction("function")
            .addArgs(
                CelExpr.ofConstantExpr(5, CelConstant.ofValue("hello")),
                CelExpr.ofConstantExpr(6, CelConstant.ofValue(5)))
            .build();

    CelExpr celExpr =
        CelExpr.newBuilder()
            .setCall(
                celCall.toBuilder()
                    .setArg(1, CelExpr.ofConstantExpr(7, CelConstant.ofValue("world")))
                    .build())
            .build();

    assertThat(celExpr.call())
        .isEqualTo(
            CelCall.newBuilder()
                .setFunction("function")
                .addArgs(
                    CelExpr.ofConstantExpr(5, CelConstant.ofValue("hello")),
                    CelExpr.ofConstantExpr(7, CelConstant.ofValue("world")))
                .build());
  }

  @Test
  public void celExprBuilder_setSelect() {
    CelSelect celSelect =
        CelSelect.newBuilder().setField("field").setOperand(CelExpr.newBuilder().build()).build();
    CelExpr celExpr = CelExpr.newBuilder().setSelect(celSelect).build();

    assertThat(celExpr.select().testOnly()).isFalse();
    assertThat(celExpr.select()).isEqualTo(celSelect);
  }

  @Test
  public void celExprBuilder_setCreateList() {
    CelCreateList celCreateList =
        CelCreateList.newBuilder()
            .addElements(CelExpr.ofConstantExpr(1, CelConstant.ofValue(2)))
            .build();
    CelExpr celExpr = CelExpr.newBuilder().setCreateList(celCreateList).build();

    assertThat(celExpr.createList()).isEqualTo(celCreateList);
  }

  @Test
  public void celExprBuilder_setCreateList_setElementByIndex() {
    CelCreateList celCreateList =
        CelCreateList.newBuilder()
            .addElements(
                CelExpr.ofConstantExpr(5, CelConstant.ofValue("hello")),
                CelExpr.ofConstantExpr(6, CelConstant.ofValue(5)))
            .build();

    CelExpr celExpr =
        CelExpr.newBuilder()
            .setCreateList(
                celCreateList.toBuilder()
                    .setElement(1, CelExpr.ofConstantExpr(7, CelConstant.ofValue("world")))
                    .build())
            .build();

    assertThat(celExpr.createList())
        .isEqualTo(
            CelCreateList.newBuilder()
                .addElements(
                    CelExpr.ofConstantExpr(5, CelConstant.ofValue("hello")),
                    CelExpr.ofConstantExpr(7, CelConstant.ofValue("world")))
                .build());
  }

  @Test
  public void celExprBuilder_setCreateStruct() {
    CelCreateStruct celCreateStruct =
        CelCreateStruct.newBuilder()
            .addEntries(
                CelCreateStruct.Entry.newBuilder()
                    .setId(1)
                    .setValue(CelExpr.newBuilder().build())
                    .setFieldKey("field_key")
                    .build())
            .build();
    CelExpr celExpr = CelExpr.newBuilder().setCreateStruct(celCreateStruct).build();

    assertThat(celExpr.createStruct().entries().get(0).optionalEntry()).isFalse();
    assertThat(celExpr.createStruct()).isEqualTo(celCreateStruct);
  }

  @Test
  public void celExprBuilder_setCreateStruct_setEntryByIndex() {
    CelCreateStruct celCreateStruct =
        CelCreateStruct.newBuilder()
            .addEntries(
                CelCreateStruct.Entry.newBuilder()
                    .setId(2)
                    .setValue(
                        CelExpr.ofConstantExpr(5, CelConstant.ofValue("hello")).toBuilder().build())
                    .setFieldKey("field_key")
                    .build(),
                CelCreateStruct.Entry.newBuilder()
                    .setId(3)
                    .setValue(
                        CelExpr.ofConstantExpr(6, CelConstant.ofValue(100)).toBuilder().build())
                    .setFieldKey("field_key")
                    .build())
            .build();

    CelExpr celExpr =
        CelExpr.newBuilder()
            .setCreateStruct(
                celCreateStruct.toBuilder()
                    .setEntry(
                        1,
                        CelCreateStruct.Entry.newBuilder()
                            .setId(4)
                            .setValue(
                                CelExpr.ofConstantExpr(6, CelConstant.ofValue("world")).toBuilder()
                                    .build())
                            .setFieldKey("field_key")
                            .build())
                    .build())
            .build();

    assertThat(celExpr.createStruct())
        .isEqualTo(
            CelCreateStruct.newBuilder()
                .addEntries(
                    CelCreateStruct.Entry.newBuilder()
                        .setId(2)
                        .setValue(
                            CelExpr.ofConstantExpr(5, CelConstant.ofValue("hello")).toBuilder()
                                .build())
                        .setFieldKey("field_key")
                        .build(),
                    CelCreateStruct.Entry.newBuilder()
                        .setId(4)
                        .setValue(
                            CelExpr.ofConstantExpr(6, CelConstant.ofValue("world")).toBuilder()
                                .build())
                        .setFieldKey("field_key")
                        .build())
                .build());
  }

  @Test
  public void celExprBuilder_setComprehension() {
    CelComprehension celComprehension =
        CelComprehension.newBuilder()
            .setIterVar("iterVar")
            .setIterRange(CelExpr.newBuilder().build())
            .setAccuVar("accuVar")
            .setAccuInit(CelExpr.newBuilder().build())
            .setLoopCondition(CelExpr.newBuilder().build())
            .setLoopStep(CelExpr.newBuilder().build())
            .setResult(CelExpr.newBuilder().build())
            .build();
    CelExpr celExpr = CelExpr.newBuilder().setComprehension(celComprehension).build();

    assertThat(celExpr.comprehension()).isEqualTo(celComprehension);
  }
}
