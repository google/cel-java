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
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
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
    CREATE_MAP(
        CelExpr.newBuilder().setCreateMap(CelCreateMap.newBuilder().build()).build(),
        Kind.CREATE_MAP),
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
            .setTarget(CelExpr.ofConstantExpr(1, CelConstant.ofValue("test")))
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
            .addArgs(CelExpr.ofConstantExpr(1, CelConstant.ofValue("test")))
            .build();

    assertThat(celCall.toBuilder().getArgs())
        .containsExactly(CelExpr.ofConstantExpr(1, CelConstant.ofValue("test")));
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
    assertThat(celExpr.toBuilder().select()).isEqualTo(celSelect);
  }

  @Test
  public void celExprBuilder_setCreateList() {
    CelCreateList celCreateList =
        CelCreateList.newBuilder()
            .addElements(CelExpr.ofConstantExpr(1, CelConstant.ofValue(2)))
            .build();
    CelExpr celExpr = CelExpr.newBuilder().setCreateList(celCreateList).build();

    assertThat(celExpr.createList()).isEqualTo(celCreateList);
    assertThat(celExpr.toBuilder().createList()).isEqualTo(celCreateList);
  }

  @Test
  public void createListBuilder_getArgs() {
    CelCreateList celCreateList =
        CelCreateList.newBuilder()
            .addElements(CelExpr.ofConstantExpr(1, CelConstant.ofValue(2)))
            .build();

    assertThat(celCreateList.toBuilder().getElements())
        .containsExactly(CelExpr.ofConstantExpr(1, CelConstant.ofValue(2)));
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
    assertThat(celExpr.toBuilder().createStruct()).isEqualTo(celCreateStruct);
  }

  @Test
  public void createStructBuilder_getArgs() {
    CelCreateStruct celCreateStruct =
        CelCreateStruct.newBuilder()
            .addEntries(
                CelCreateStruct.Entry.newBuilder()
                    .setId(1)
                    .setValue(CelExpr.newBuilder().build())
                    .setFieldKey("field_key")
                    .build())
            .build();

    assertThat(celCreateStruct.toBuilder().getEntries())
        .containsExactly(
            CelCreateStruct.Entry.newBuilder()
                .setId(1)
                .setValue(CelExpr.newBuilder().build())
                .setFieldKey("field_key")
                .build());
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
    if (!testCase.expectedExprKind.equals(Kind.CREATE_LIST)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::createList);
      assertThrows(
          UnsupportedOperationException.class, () -> testCase.expr.toBuilder().createList());
    }
    if (!testCase.expectedExprKind.equals(Kind.CREATE_STRUCT)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::createStruct);
      assertThrows(
          UnsupportedOperationException.class, () -> testCase.expr.toBuilder().createStruct());
    }
    if (!testCase.expectedExprKind.equals(Kind.CREATE_MAP)) {
      assertThrows(UnsupportedOperationException.class, testCase.expr::createMap);
      assertThrows(
          UnsupportedOperationException.class, () -> testCase.expr.toBuilder().createMap());
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
    if (!testCase.expectedExprKind.equals(Kind.CREATE_LIST)) {
      assertThat(testCase.expr.createListOrDefault()).isEqualTo(CelCreateList.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.CREATE_STRUCT)) {
      assertThat(testCase.expr.createStructOrDefault())
          .isEqualTo(CelCreateStruct.newBuilder().build());
    }
    if (!testCase.expectedExprKind.equals(Kind.CREATE_MAP)) {
      assertThat(testCase.expr.createMapOrDefault()).isEqualTo(CelCreateMap.newBuilder().build());
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
      case CREATE_LIST:
        assertThat(testCase.expr.createListOrDefault()).isEqualTo(testCase.expr.createList());
        break;
      case CREATE_STRUCT:
        assertThat(testCase.expr.createStructOrDefault()).isEqualTo(testCase.expr.createStruct());
        break;
      case CREATE_MAP:
        assertThat(testCase.expr.createMapOrDefault()).isEqualTo(testCase.expr.createMap());
        break;
      case COMPREHENSION:
        assertThat(testCase.expr.comprehensionOrDefault()).isEqualTo(testCase.expr.comprehension());
        break;
    }
  }

  @Test
  public void celCreateMapEntry_keyOrValueNotSet_throws() {
    assertThrows(IllegalStateException.class, () -> CelCreateMap.Entry.newBuilder().build());
    assertThrows(
        IllegalStateException.class,
        () -> CelCreateMap.Entry.newBuilder().setKey(CelExpr.ofNotSet(1)).build());
    assertThrows(
        IllegalStateException.class,
        () -> CelCreateMap.Entry.newBuilder().setValue(CelExpr.ofNotSet(1)).build());
  }

  @Test
  public void celCreateMapEntry_default() {
    CelCreateMap.Entry entry =
        CelCreateMap.Entry.newBuilder()
            .setKey(CelExpr.ofNotSet(1))
            .setValue(CelExpr.ofNotSet(2))
            .build();

    assertThat(entry.id()).isEqualTo(0);
    assertThat(entry.optionalEntry()).isFalse();
  }

  @Test
  public void celCreateStructEntry_fieldKeyOrValueNotSet_throws() {
    assertThrows(IllegalStateException.class, () -> CelCreateStruct.Entry.newBuilder().build());
    assertThrows(
        IllegalStateException.class,
        () -> CelCreateStruct.Entry.newBuilder().setFieldKey("fieldKey").build());
    assertThrows(
        IllegalStateException.class,
        () -> CelCreateStruct.Entry.newBuilder().setValue(CelExpr.ofNotSet(1)).build());
  }

  @Test
  public void celCreateStructEntry_default() {
    CelCreateStruct.Entry entry =
        CelCreateStruct.Entry.newBuilder()
            .setFieldKey("fieldKey")
            .setValue(CelExpr.ofNotSet(1))
            .build();

    assertThat(entry.id()).isEqualTo(0);
    assertThat(entry.optionalEntry()).isFalse();
  }
}
