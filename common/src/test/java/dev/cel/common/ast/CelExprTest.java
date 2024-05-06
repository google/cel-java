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
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.CelMap;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.CelStruct;
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
    MAP(CelExpr.newBuilder().setMap(CelMap.newBuilder().build()).build(), Kind.MAP),
    LIST(CelExpr.newBuilder().setList(CelList.newBuilder().build()).build(), Kind.LIST),
    STRUCT(CelExpr.newBuilder().setStruct(CelStruct.newBuilder().build()).build(), Kind.STRUCT),
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
    assertThat(celExpr.toBuilder().constant()).isEqualTo(celConstant);
  }

  @Test
  public void celExprBuilder_setIdent() {
    CelIdent celIdent = CelIdent.newBuilder().setName("ident").build();
    CelExpr celExpr = CelExpr.newBuilder().setIdent(celIdent).build();

    assertThat(celExpr.ident()).isEqualTo(celIdent);
    assertThat(celExpr.toBuilder().ident()).isEqualTo(celIdent);
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
    assertThat(celExpr.toBuilder().call()).isEqualTo(celCall);
  }

  @Test
  public void celExprBuilder_setCall_clearTarget() {
    CelCall celCall =
        CelCall.newBuilder()
            .setFunction("function")
            .setTarget(CelExpr.ofConstant(1, CelConstant.ofValue("test")))
            .build();
    CelExpr celExpr =
        CelExpr.newBuilder().setCall(celCall.toBuilder().clearTarget().build()).build();

    assertThat(celExpr.call()).isEqualTo(CelCall.newBuilder().setFunction("function").build());
    assertThat(celExpr.toBuilder().call())
        .isEqualTo(CelCall.newBuilder().setFunction("function").build());
  }

  @Test
  public void callBuilder_getArgs() {
    CelCall celCall =
        CelCall.newBuilder()
            .setFunction("function")
            .addArgs(CelExpr.ofConstant(1, CelConstant.ofValue("test")))
            .build();

    assertThat(celCall.toBuilder().getArgs())
        .containsExactly(CelExpr.ofConstant(1, CelConstant.ofValue("test")));
  }

  @Test
  public void celExprBuilder_setCall_setArgByIndex() {
    CelCall celCall =
        CelCall.newBuilder()
            .setFunction("function")
            .addArgs(
                CelExpr.ofConstant(5, CelConstant.ofValue("hello")),
                CelExpr.ofConstant(6, CelConstant.ofValue(5)))
            .build();

    CelExpr celExpr =
        CelExpr.newBuilder()
            .setCall(
                celCall.toBuilder()
                    .setArg(1, CelExpr.ofConstant(7, CelConstant.ofValue("world")))
                    .build())
            .build();

    assertThat(celExpr.call())
        .isEqualTo(
            CelCall.newBuilder()
                .setFunction("function")
                .addArgs(
                    CelExpr.ofConstant(5, CelConstant.ofValue("hello")),
                    CelExpr.ofConstant(7, CelConstant.ofValue("world")))
                .build());
  }

  @Test
  public void celExprBuilder_setSelect() {
    CelSelect celSelect =
        CelSelect.newBuilder().setField("field").setOperand(CelExpr.newBuilder().build()).build();
    CelExpr celExpr = CelExpr.newBuilder().setSelect(celSelect).build();

    assertThat(celExpr.select().testOnly()).isFalse();
    assertThat(celExpr.select()).isEqualTo(celSelect);
    assertThat(celExpr.toBuilder().select()).isEqualTo(celSelect);
  }

  @Test
  public void celExprBuilder_setList() {
    CelList celList =
        CelList.newBuilder().addElements(CelExpr.ofConstant(1, CelConstant.ofValue(2))).build();
    CelExpr celExpr = CelExpr.newBuilder().setList(celList).build();

    assertThat(celExpr.list()).isEqualTo(celList);
    assertThat(celExpr.toBuilder().list()).isEqualTo(celList);
  }

  @Test
  public void listBuilder_getArgs() {
    CelList celList =
        CelList.newBuilder().addElements(CelExpr.ofConstant(1, CelConstant.ofValue(2))).build();

    assertThat(celList.toBuilder().getElements())
        .containsExactly(CelExpr.ofConstant(1, CelConstant.ofValue(2)));
  }

  @Test
  public void celExprBuilder_setList_setElementByIndex() {
    CelList celList =
        CelList.newBuilder()
            .addElements(
                CelExpr.ofConstant(5, CelConstant.ofValue("hello")),
                CelExpr.ofConstant(6, CelConstant.ofValue(5)))
            .build();

    CelExpr celExpr =
        CelExpr.newBuilder()
            .setList(
                celList.toBuilder()
                    .setElement(1, CelExpr.ofConstant(7, CelConstant.ofValue("world")))
                    .build())
            .build();

    assertThat(celExpr.list())
        .isEqualTo(
            CelList.newBuilder()
                .addElements(
                    CelExpr.ofConstant(5, CelConstant.ofValue("hello")),
                    CelExpr.ofConstant(7, CelConstant.ofValue("world")))
                .build());
  }

  @Test
  public void celExprBuilder_setStruct() {
    CelStruct celStruct =
        CelStruct.newBuilder()
            .addEntries(
                CelStruct.Entry.newBuilder()
                    .setId(1)
                    .setValue(CelExpr.newBuilder().build())
                    .setFieldKey("field_key")
                    .build())
            .build();
    CelExpr celExpr = CelExpr.newBuilder().setStruct(celStruct).build();

    assertThat(celExpr.struct().entries().get(0).optionalEntry()).isFalse();
    assertThat(celExpr.struct()).isEqualTo(celStruct);
    assertThat(celExpr.toBuilder().struct()).isEqualTo(celStruct);
  }

  @Test
  public void structBuilder_getArgs() {
    CelStruct celStruct =
        CelStruct.newBuilder()
            .addEntries(
                CelStruct.Entry.newBuilder()
                    .setId(1)
                    .setValue(CelExpr.newBuilder().build())
                    .setFieldKey("field_key")
                    .build())
            .build();

    assertThat(celStruct.toBuilder().getEntries())
        .containsExactly(
            CelStruct.Entry.newBuilder()
                .setId(1)
                .setValue(CelExpr.newBuilder().build())
                .setFieldKey("field_key")
                .build());
  }

  @Test
  public void celExprBuilder_setStruct_setEntryByIndex() {
    CelStruct celStruct =
        CelStruct.newBuilder()
            .addEntries(
                CelStruct.Entry.newBuilder()
                    .setId(2)
                    .setValue(
                        CelExpr.ofConstant(5, CelConstant.ofValue("hello")).toBuilder().build())
                    .setFieldKey("field_key")
                    .build(),
                CelStruct.Entry.newBuilder()
                    .setId(3)
                    .setValue(CelExpr.ofConstant(6, CelConstant.ofValue(100)).toBuilder().build())
                    .setFieldKey("field_key")
                    .build())
            .build();

    CelExpr celExpr =
        CelExpr.newBuilder()
            .setStruct(
                celStruct.toBuilder()
                    .setEntry(
                        1,
                        CelStruct.Entry.newBuilder()
                            .setId(4)
                            .setValue(
                                CelExpr.ofConstant(6, CelConstant.ofValue("world")).toBuilder()
                                    .build())
                            .setFieldKey("field_key")
                            .build())
                    .build())
            .build();

    assertThat(celExpr.struct())
        .isEqualTo(
            CelStruct.newBuilder()
                .addEntries(
                    CelStruct.Entry.newBuilder()
                        .setId(2)
                        .setValue(
                            CelExpr.ofConstant(5, CelConstant.ofValue("hello")).toBuilder().build())
                        .setFieldKey("field_key")
                        .build(),
                    CelStruct.Entry.newBuilder()
                        .setId(4)
                        .setValue(
                            CelExpr.ofConstant(6, CelConstant.ofValue("world")).toBuilder().build())
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
    assertThat(celExpr.toBuilder().comprehension()).isEqualTo(celComprehension);
  }

  @Test
  public void getUnderlyingExpression_unmatchedKind_throws(
      @TestParameter BuilderExprKindTestCase testCase) {
    if (!testCase.expectedExprKind.equals(Kind.NOT_SET)) {
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.exprKind().notSet());
      assertThrows(
          UnsupportedOperationException.class, () -> testCase.expr.toBuilder().exprKind().notSet());
    }
    if (!testCase.expectedExprKind.equals(Kind.CONSTANT)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::constant);
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.toBuilder().constant());
    }
    if (!testCase.expectedExprKind.equals(Kind.IDENT)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::ident);
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.toBuilder().ident());
    }
    if (!testCase.expectedExprKind.equals(Kind.SELECT)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::select);
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.toBuilder().select());
    }
    if (!testCase.expectedExprKind.equals(Kind.CALL)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::call);
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.toBuilder().call());
    }
    if (!testCase.expectedExprKind.equals(Kind.LIST)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::list);
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.toBuilder().list());
    }
    if (!testCase.expectedExprKind.equals(Kind.STRUCT)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::struct);
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.toBuilder().struct());
    }
    if (!testCase.expectedExprKind.equals(Kind.MAP)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::map);
      assertThrows(UnsupportedOperationException.class, () -> testCase.expr.toBuilder().map());
    }
    if (!testCase.expectedExprKind.equals(Kind.COMPREHENSION)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::comprehension);
      assertThrows(
          UnsupportedOperationException.class, () -> testCase.expr.toBuilder().comprehension());
    }
  }

  @Test
  public void getDefault_unmatchedKind_returnsDefaultInstance(
      @TestParameter BuilderExprKindTestCase testCase) {
    if (!testCase.expectedExprKind.equals(Kind.CONSTANT)) {
      assertThat(testCase.expr.constantOrDefault()).isEqualTo(CelConstant.ofNotSet());
    }
    if (!testCase.expectedExprKind.equals(Kind.IDENT)) {
      assertThat(testCase.expr.identOrDefault()).isEqualTo(CelIdent.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.SELECT)) {
      assertThat(testCase.expr.selectOrDefault()).isEqualTo(CelSelect.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.CALL)) {
      assertThat(testCase.expr.callOrDefault()).isEqualTo(CelCall.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.LIST)) {
      assertThat(testCase.expr.listOrDefault()).isEqualTo(CelList.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.STRUCT)) {
      assertThat(testCase.expr.structOrDefault()).isEqualTo(CelStruct.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.MAP)) {
      assertThat(testCase.expr.mapOrDefault()).isEqualTo(CelMap.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.COMPREHENSION)) {
      assertThat(testCase.expr.comprehensionOrDefault())
          .isEqualTo(CelComprehension.newBuilder().build());
    }
  }

  @Test
  public void getDefault_matchedKind_returnsUnderlyingExpression(
      @TestParameter BuilderExprKindTestCase testCase) {
    switch (testCase.expectedExprKind) {
      case NOT_SET:
        // no-op
        break;
      case CONSTANT:
        assertThat(testCase.expr.constantOrDefault()).isEqualTo(testCase.expr.constant());
        break;
      case IDENT:
        assertThat(testCase.expr.identOrDefault()).isEqualTo(testCase.expr.ident());
        break;
      case SELECT:
        assertThat(testCase.expr.selectOrDefault()).isEqualTo(testCase.expr.select());
        break;
      case CALL:
        assertThat(testCase.expr.callOrDefault()).isEqualTo(testCase.expr.call());
        break;
      case LIST:
        assertThat(testCase.expr.listOrDefault()).isEqualTo(testCase.expr.list());
        break;
      case STRUCT:
        assertThat(testCase.expr.structOrDefault()).isEqualTo(testCase.expr.struct());
        break;
      case MAP:
        assertThat(testCase.expr.mapOrDefault()).isEqualTo(testCase.expr.map());
        break;
      case COMPREHENSION:
        assertThat(testCase.expr.comprehensionOrDefault()).isEqualTo(testCase.expr.comprehension());
        break;
    }
  }

  @Test
  public void celMapEntry_keyOrValueNotSet_throws() {
    assertThrows(IllegalStateException.class, () -> CelMap.Entry.newBuilder().build());
    assertThrows(
        IllegalStateException.class,
        () -> CelMap.Entry.newBuilder().setKey(CelExpr.ofNotSet(1)).build());
    assertThrows(
        IllegalStateException.class,
        () -> CelMap.Entry.newBuilder().setValue(CelExpr.ofNotSet(1)).build());
  }

  @Test
  public void celMapEntry_default() {
    CelMap.Entry entry =
        CelMap.Entry.newBuilder().setKey(CelExpr.ofNotSet(1)).setValue(CelExpr.ofNotSet(2)).build();

    assertThat(entry.id()).isEqualTo(0);
    assertThat(entry.optionalEntry()).isFalse();
  }

  @Test
  public void celStructEntry_fieldKeyOrValueNotSet_throws() {
    assertThrows(IllegalStateException.class, () -> CelStruct.Entry.newBuilder().build());
    assertThrows(
        IllegalStateException.class,
        () -> CelStruct.Entry.newBuilder().setFieldKey("fieldKey").build());
    assertThrows(
        IllegalStateException.class,
        () -> CelStruct.Entry.newBuilder().setValue(CelExpr.ofNotSet(1)).build());
  }

  @Test
  public void celStructEntry_default() {
    CelStruct.Entry entry =
        CelStruct.Entry.newBuilder().setFieldKey("fieldKey").setValue(CelExpr.ofNotSet(1)).build();

    assertThat(entry.id()).isEqualTo(0);
    assertThat(entry.optionalEntry()).isFalse();
  }
}
